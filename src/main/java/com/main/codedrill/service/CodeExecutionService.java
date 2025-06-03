package com.main.codedrill.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

@Service
public class CodeExecutionService {

    @Value("${docker.timeout_seconds:10}")
    private int timeoutSeconds;

    @Value("${docker.image:apcsa-coderunner:latest}")
    private String dockerImage;

    @Value("${docker.memory_limit:128m}")
    private String memoryLimit;

    @Value("${docker.cpu_limit:0.5}")
    private String cpuLimit;

    @Value("${docker.process_limit:32}")
    private int processLimit;

    @Value("${docker.network_disabled:true}")
    private boolean networkDisabled;

    @Value("${docker.enabled:true}")
    private boolean dockerEnabled;

    // Security configuration
    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${security.strict_mode:true}")
    private boolean strictMode;

    private final UserService userService;
    private final Queue<CompletableFuture<String>> executionQueue = new LinkedList<>();
    private final Object queueLock = new Object();
    private final ExecutorService queueProcessor = Executors.newSingleThreadExecutor();
    private Semaphore resourceSemaphore;
    private volatile boolean processingQueue = false;

    // Malicious code detector
    private final DockerAwareMaliciousCodeDetector codeDetector = new DockerAwareMaliciousCodeDetector();

    @Autowired
    public CodeExecutionService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Initialize the service after Spring has injected all properties
     */
    @PostConstruct
    private void initialize() {
        initializeResourceSemaphore();
        startQueueProcessor();
        System.out.println("Security scanning " + (securityEnabled ? "ENABLED" : "DISABLED"));
        System.out.println("Strict mode " + (strictMode ? "ENABLED" : "DISABLED"));
    }

    /**
     * Initialize the semaphore based on available system resources.
     */
    private void initializeResourceSemaphore() {
        int systemCores = Runtime.getRuntime().availableProcessors();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();

        // Use the available memory (max - total + free)
        long availableMemory = maxMemory - totalMemory + Runtime.getRuntime().freeMemory();

        // Calculate maximum concurrent executions based on system resources
        int maxByCpu = Math.max(1, systemCores / 2); // Allow half of the system cores
        long containerMemoryBytes = parseMemoryLimit(memoryLimit);
        int maxByMemory = Math.max(1, (int) (availableMemory / containerMemoryBytes)); // Allow containers based on memory

        int maxConcurrentExecutions = Math.min(maxByCpu * 2, maxByMemory);

        // Ensure we have at least 1 concurrent execution and not more than 16
        maxConcurrentExecutions = Math.min(16, maxConcurrentExecutions);

        this.resourceSemaphore = new Semaphore(maxConcurrentExecutions);

        System.out.println("Initialized CodeExecutionService with max " + maxConcurrentExecutions + " concurrent executions");
        System.out.println("System cores: " + systemCores + ", Available memory: " + (availableMemory / 1024 / 1024) + "MB");
    }

    private long parseMemoryLimit(String memoryLimit) {
        if (memoryLimit == null || memoryLimit.trim().isEmpty()) {
            return 128 * 1024 * 1024;
        }

        String limit = memoryLimit.toLowerCase().trim();

        if (limit.endsWith("m")) {
            return Long.parseLong(limit.replace("m", "")) * 1024 * 1024;
        } else if (limit.endsWith("g")) {
            return Long.parseLong(limit.replace("g", "")) * 1024 * 1024 * 1024;
        } else if (limit.endsWith("k")) {
            return Long.parseLong(limit.replace("k", "")) * 1024;
        } else {
            try {
                return Long.parseLong(limit);
            } catch (NumberFormatException e) {
                System.err.println("Invalid memory limit format: " + memoryLimit + ", using default 128MB");
                return 128 * 1024 * 1024;
            }
        }
    }

    /**
     * Start a background thread to process the execution queue.
     */
    private void startQueueProcessor() {
        queueProcessor.submit(() -> {
            boolean wasProcessing = false;
            while (!Thread.currentThread().isInterrupted()) {
                CompletableFuture<String> task = null;

                synchronized (queueLock) {
                    if (!executionQueue.isEmpty()) {
                        task = executionQueue.poll();
                    }
                }

                if (task != null) {
                    processingQueue = true;
                    wasProcessing = true;
                } else {
                    boolean previouslyProcessing = processingQueue;
                    processingQueue = false;
                    if (previouslyProcessing && wasProcessing) {
                        userService.setCurrentExecutions(false);
                        wasProcessing = false;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    /**
     * Execute Java code in a Docker container with security scanning
     *
     * @param code The Java code to execute
     * @return The execution output
     * @throws Exception if execution fails
     */
    public String executeJavaCode(String code) throws Exception {
        // Security check first
        if (securityEnabled) {
            DockerAwareMaliciousCodeDetector.MaliciousCodeResult securityResult = codeDetector.analyzeCode(code);

            if (securityResult.malicious()) {
                String securityMessage = formatSecurityMessage(securityResult);

                // Log security violation for monitoring
                System.err.println("SECURITY VIOLATION - User: " + getCurrentUserInfo() +
                        " - Risk Level: " + securityResult.riskLevel() +
                        " - Reasons: " + securityResult.reasons());

                // In strict mode -> block all malicious code
                if (strictMode || securityResult.riskLevel() == DockerAwareMaliciousCodeDetector.RiskLevel.CRITICAL) {
                    return securityMessage;
                }

                System.out.println("WARNING: Potentially risky code detected but execution allowed in non-strict mode");
            }
        }

        if (!dockerEnabled) {
            return simulateExecution(code);
        }

        if (resourceSemaphore.tryAcquire()) {
            try {
                return executeInDocker(code);
            } finally {
                resourceSemaphore.release();
                processNextInQueue();
            }
        } else {
            userService.setCurrentExecutions(true);
            return addToQueueAndWait(code);
        }
    }

    private String formatSecurityMessage(DockerAwareMaliciousCodeDetector.MaliciousCodeResult result) {
        StringBuilder message = new StringBuilder();
        message.append("🛡️ SECURITY ALERT: Code execution blocked\n\n");
        message.append("Risk Level: ").append(result.riskLevel()).append("\n");
        message.append("Detected Issues:\n");

        String[] reasons = result.reasons().split(";");
        for (String reason : reasons) {
            if (!reason.trim().isEmpty()) {
                message.append("• ").append(reason.trim()).append("\n");
            }
        }

        message.append("\n📋 Security Guidelines:\n");
        message.append("• Use only standard Java APIs for assignments\n");
        message.append("• Avoid system calls, file operations, and network access\n");
        message.append("• Focus on algorithm implementation and data structures\n");
        message.append("• Contact instructor if you believe this is a false positive\n");

        return message.toString();
    }

    private String getCurrentUserInfo() {
        try {
            return userService != null ? "UserService available" : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String addToQueueAndWait(String code) {

        CompletableFuture<String> queuedTask = CompletableFuture.supplyAsync(() -> {
            try {
                resourceSemaphore.acquire();
                try {
                    return executeInDocker(code);
                } finally {
                    resourceSemaphore.release();
                    processNextInQueue();
                }
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        });

        synchronized (queueLock) {
            executionQueue.offer(queuedTask);
        }

        try {
            return queuedTask.get(timeoutSeconds * 2L, TimeUnit.SECONDS); // Allow double timeout for queued tasks
        } catch (Exception e) {
            return "ERROR: Execution timed out or failed: " + e.getMessage();
        }
    }

    private void processNextInQueue() {
        synchronized (queueLock) {
            if (!executionQueue.isEmpty() && !processingQueue) {
                queueLock.notify();
            }
        }
    }

    /**
     * Execute code in a Docker container for security
     */
    private String executeInDocker(String code) throws Exception {
        String containerId = "coderunner-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            Process process = getProcess(code, containerId);

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "Execution timeout - your code took too long to run";
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            StringBuilder errors = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errors.append(line).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                return extractMainError(errors.toString());
            }

            return output.toString();
        } catch (Exception e) {
            try {
                new ProcessBuilder("docker", "rm", "-f", containerId).start();
            } catch (Exception cleanupEx) {
                // Ignore cleanup errors
            }
            throw e;
        }
    }

    private Process getProcess(String code, String containerId) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--name", containerId,
                "--rm", "-i",
                networkDisabled ? "--network=none" : "",
                "--memory=" + memoryLimit,
                "--cpus=" + cpuLimit,
                "--ulimit", "nproc=" + processLimit + ":" + (processLimit * 2),
                dockerImage
        );

        pb.command().removeIf(String::isEmpty);

        Process process = pb.start();

        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write(code);
        }
        return process;
    }

    private String extractMainError(String errorOutput) {
        if (errorOutput.startsWith("Compilation Error")) {
            Pattern pattern = Pattern.compile("(\\w+\\.java:\\d+: error:.+?(?=\\n\\s*\\^|$))", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(errorOutput);

            if (matcher.find()) {
                return "Compilation Error: " + matcher.group(1).trim();
            }
        }

        String[] lines = errorOutput.split("\n");
        if (lines.length > 0) {
            StringBuilder result = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                result.append(line).append("\n");
                count++;
                if (count >= 3) break;
            }
            return result.toString();
        }
        return errorOutput;
    }

    private String simulateExecution(String code) {
        if (code.contains("System.out.print")) {
            StringBuilder output = new StringBuilder();
            String[] lines = code.split("\n");
            for (String line : lines) {
                if (line.contains("System.out.print")) {
                    try {
                        int startIndex = line.indexOf("(\"") + 2;
                        int endIndex = line.lastIndexOf("\")");
                        if (startIndex > 1 && endIndex > startIndex) {
                            String content = line.substring(startIndex, endIndex);
                            output.append(content);
                            if (line.contains("println")) {
                                output.append("\n");
                            }
                        }
                    } catch (Exception ignored) {
                        // Ignore
                    }
                }
            }
            if (!output.isEmpty()) {
                return output.toString();
            }
        }
        return "Code executed successfully, but no output was detected.";
    }

    /**
     * Cleanup method called when the service is destroyed
     */
    @PreDestroy
    public void destroy() {
        if (!queueProcessor.isShutdown()) {
            queueProcessor.shutdown();
            try {
                if (!queueProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                    queueProcessor.shutdownNow();
                }
            } catch (InterruptedException e) {
                queueProcessor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Docker-aware malicious code detector optimized for containerized Java execution
     */
    private static class DockerAwareMaliciousCodeDetector {

        // Container escape and privilege escalation patterns
        private static final Pattern CONTAINER_ESCAPE_PATTERN = Pattern.compile(
                "(?i)(?:/proc/|/sys/|/dev/|/host|/var/run/docker\\.sock|" +
                        "chroot|pivot_root|unshare|nsenter|capsh|" +
                        "docker|kubernetes|k8s|container|cgroup)",
                Pattern.CASE_INSENSITIVE
        );

        // System execution - more restrictive for container environment
        private static final Pattern SYSTEM_EXEC_PATTERN = Pattern.compile(
                "(?i)runtime\\.getruntime\\(\\)\\.exec|processbuilder|" +
                        "new\\s+process(?:builder)?|exec|system\\s*\\(|" +
                        "bash|sh|cmd|powershell|/bin/",
                Pattern.CASE_INSENSITIVE
        );

        // File system attacks specific to containers
        private static final Pattern DANGEROUS_FILE_PATTERN = Pattern.compile(
                "(?i)(?:file(?:writer|outputstream|inputstream|reader)|" +
                        "(?:buffered|print)writer|randomaccessfile|nio\\.file\\.files|" +
                        "path\\.of|paths\\.get)\\s*\\.|" +
                        "\\.(?:write|delete|mkdir|createdirectories|copy|move|createfile)\\s*\\(|" +
                        "\\.\\.[/\\\\]|[/\\\\]etc[/\\\\]|[/\\\\]proc[/\\\\]|" +
                        "[/\\\\]sys[/\\\\]|[/\\\\]dev[/\\\\]|[/\\\\]tmp[/\\\\]",
                Pattern.CASE_INSENSITIVE
        );

        // Network operations (even though network is disabled, code might try)
        private static final Pattern NETWORK_PATTERN = Pattern.compile(
                "(?i)(?:socket|serversocket|url|httpclient|urlconnection|" +
                        "httpurlconnection|datagramsocket)\\.|\\.connect\\s*\\(|" +
                        "new\\s+(?:socket|url|httpclient|serversocket)|" +
                        "inetaddress|networkinterface|localhost|127\\.0\\.0\\.1",
                Pattern.CASE_INSENSITIVE
        );

        // Reflection and dynamic code loading
        private static final Pattern REFLECTION_PATTERN = Pattern.compile(
                "(?i)class\\.forname|getdeclaredmethod|setaccessible|invoke\\s*\\(|" +
                        "method\\.invoke|field\\.set|constructor\\.newinstance|" +
                        "unsafe|sun\\.misc|methodhandle",
                Pattern.CASE_INSENSITIVE
        );

        // JVM and system manipulation
        private static final Pattern JVM_MANIPULATION_PATTERN = Pattern.compile(
                "(?i)system\\.(?:exit|halt|gc|load|loadlibrary|setproperty|" +
                        "setsecuritymanager|getenv|getproperty)|" +
                        "runtime\\.(?:halt|exit|gc|freeMemory|totalMemory)|" +
                        "shutdownhook|thread\\.(?:sleep|interrupt)|" +
                        "securitymanager|policy\\.setpolicy",
                Pattern.CASE_INSENSITIVE
        );

        // Resource exhaustion attacks
        private static final Pattern RESOURCE_ATTACK_PATTERN = Pattern.compile(
                "(?i)while\\s*\\(\\s*true\\s*\\)|for\\s*\\(\\s*;\\s*;\\s*\\)|" +
                        "thread\\.sleep\\s*\\(\\s*0\\s*\\)|new\\s+thread|" +
                        "executorservice|threadpool|\\bnew\\s+\\w+\\[\\s*\\d{6,}\\s*]|" +
                        "arraylist\\s*\\(\\s*\\d{6,}\\s*\\)",
                Pattern.CASE_INSENSITIVE
        );

        // Serialization attacks
        private static final Pattern SERIALIZATION_PATTERN = Pattern.compile(
                "(?i)objectinputstream|objectoutputstream|serializable|" +
                        "readobject|writeobject|externali[sz]able|" +
                        "\\bac ed 00 05|rmi|jndi|ldap://",
                Pattern.CASE_INSENSITIVE
        );

        // Class manipulation and bytecode
        private static final Pattern BYTECODE_PATTERN = Pattern.compile(
                "(?i)classloader|defineclass|loadclass|findclass|" +
                        "asm\\.|javassist|cglib|bytebuddy|" +
                        "instrumentation|transform|retransform",
                Pattern.CASE_INSENSITIVE
        );

        // Dangerous keywords that shouldn't appear in student code
        private static final Set<String> FORBIDDEN_KEYWORDS = new HashSet<>(Arrays.asList(
                "native", "jni", "sun.misc", "com.sun", "jdk.internal",
                "unsafe", "privileged", "doPrivileged", "accessController"
        ));

        /**
         * Comprehensive malicious code analysis for Docker environment
         */
        public MaliciousCodeResult analyzeCode(String code) {
            if (code == null || code.isEmpty()) {
                return new MaliciousCodeResult(false, RiskLevel.NONE, "No code provided");
            }

            String normalizedCode = normalizeCode(code);
            StringBuilder detectionReasons = new StringBuilder();
            RiskLevel maxRiskLevel = RiskLevel.NONE;

            // Critical security checks for container environment
            maxRiskLevel = checkAndUpdate(CONTAINER_ESCAPE_PATTERN, normalizedCode,
                    "Container escape attempt detected", RiskLevel.CRITICAL, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(SYSTEM_EXEC_PATTERN, normalizedCode,
                    "System command execution detected", RiskLevel.CRITICAL, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(JVM_MANIPULATION_PATTERN, normalizedCode,
                    "JVM manipulation detected", RiskLevel.HIGH, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(BYTECODE_PATTERN, normalizedCode,
                    "Bytecode manipulation detected", RiskLevel.HIGH, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(SERIALIZATION_PATTERN, normalizedCode,
                    "Serialization attack patterns detected", RiskLevel.HIGH, maxRiskLevel, detectionReasons);

            // Medium risk patterns
            maxRiskLevel = checkAndUpdate(DANGEROUS_FILE_PATTERN, normalizedCode,
                    "Dangerous file operations detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(NETWORK_PATTERN, normalizedCode,
                    "Network operations detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(REFLECTION_PATTERN, normalizedCode,
                    "Reflection API abuse detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(RESOURCE_ATTACK_PATTERN, normalizedCode,
                    "Resource exhaustion patterns detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

            // Check forbidden keywords
            String lowerCode = normalizedCode.toLowerCase();
            for (String keyword : FORBIDDEN_KEYWORDS) {
                if (lowerCode.contains(keyword.toLowerCase())) {
                    maxRiskLevel = updateRiskLevel(RiskLevel.HIGH, maxRiskLevel);
                    detectionReasons.append("Forbidden keyword detected: ").append(keyword).append("; ");
                }
            }

            maxRiskLevel = performHeuristicChecks(normalizedCode, maxRiskLevel, detectionReasons);

            boolean isMalicious = maxRiskLevel.ordinal() >= RiskLevel.MEDIUM.ordinal();
            return new MaliciousCodeResult(isMalicious, maxRiskLevel, detectionReasons.toString());
        }

        /**
         * Perform additional heuristic security checks
         */
        private RiskLevel performHeuristicChecks(String code, RiskLevel currentMax, StringBuilder reasons) {
            RiskLevel maxLevel = currentMax;

            if (code.matches(".*\"[^\"]*\\\\x[0-9a-fA-F]{2}[^\"]*\".*")) {
                maxLevel = updateRiskLevel(RiskLevel.MEDIUM, maxLevel);
                reasons.append("Hex-encoded strings detected (possible obfuscation); ");
            }

            int nestedLoopCount = countNestedLoops(code);
            if (nestedLoopCount > 3) {
                maxLevel = updateRiskLevel(RiskLevel.MEDIUM, maxLevel);
                reasons.append("Excessive nested loops detected (DoS risk); ");
            }

            if (code.matches(".*new\\s+\\w+\\[\\s*\\d{5,}\\s*].*")) {
                maxLevel = updateRiskLevel(RiskLevel.MEDIUM, maxLevel);
                reasons.append("Large array allocation detected (memory exhaustion risk); ");
            }

            return maxLevel;
        }

        /**
         * Count nested loop structures
         */
        private int countNestedLoops(String code) {
            int maxNesting = 0;
            int currentNesting = 0;

            String[] lines = code.split("\n");
            for (String line : lines) {
                if (line.matches(".*\\b(?:for|while|do)\\b.*")) {
                    currentNesting++;
                    maxNesting = Math.max(maxNesting, currentNesting);
                }
                int openBraces = line.length() - line.replace("{", "").length();
                int closeBraces = line.length() - line.replace("}", "").length();
                currentNesting = Math.max(0, currentNesting + openBraces - closeBraces);
            }

            return maxNesting;
        }

        private RiskLevel checkAndUpdate(Pattern pattern, String code, String reason,
                                         RiskLevel riskLevel, RiskLevel currentMax, StringBuilder reasons) {
            if (pattern.matcher(code).find()) {
                reasons.append(reason).append("; ");
                return updateRiskLevel(riskLevel, currentMax);
            }
            return currentMax;
        }

        private RiskLevel updateRiskLevel(RiskLevel newLevel, RiskLevel currentMax) {
            return newLevel.ordinal() > currentMax.ordinal() ? newLevel : currentMax;
        }

        /**
         * Enhanced code normalization
         */
        private String normalizeCode(String code) {
            String normalized = code.replaceAll("//.*$", "");
            normalized = normalized.replaceAll("/\\*.*?\\*/", " ");
            normalized = normalized.replaceAll("\"[^\"]*\"", "\"STRING\"");
            normalized = normalized.replaceAll("\\s+", " ");
            return normalized.trim();
        }

        // Result classes
        public record MaliciousCodeResult(boolean malicious, RiskLevel riskLevel, String reasons) {

            @Override
            public String toString() {
                return String.format("MaliciousCodeResult{malicious=%s, riskLevel=%s, reasons='%s'}",
                        malicious, riskLevel, reasons);
            }
        }

        public enum RiskLevel {
            NONE, LOW, MEDIUM, HIGH, CRITICAL
        }
    }
}