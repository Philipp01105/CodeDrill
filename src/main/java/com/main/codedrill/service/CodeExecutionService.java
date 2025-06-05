package com.main.codedrill.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Map;

@Service
public class CodeExecutionService {

    @Value("${docker.timeout_seconds:10}")
    private int timeoutSeconds;

    @Value("${docker.test_timeout_seconds:30}")
    private int testTimeoutSeconds;

    @Value("${docker.image:codedrill:latest}")
    private String dockerImage;

    @Value("${docker.junit_image:codedrill:latest}")
    private String junitDockerImage;

    @Value("${docker.memory_limit:128m}")
    private String memoryLimit;

    @Value("${docker.test_memory_limit:256m}")
    private String testMemoryLimit;

    @Value("${docker.cpu_limit:0.5}")
    private String cpuLimit;

    @Value("${docker.test_cpu_limit:1.0}")
    private String testCpuLimit;

    @Value("${docker.process_limit:32}")
    private int processLimit;

    @Value("${docker.test_process_limit:64}")
    private int testProcessLimit;

    @Value("${docker.network_disabled:true}")
    private boolean networkDisabled;

    @Value("${docker.enabled:true}")
    private boolean dockerEnabled;

    // Security configuration
    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${security.strict_mode:true}")
    private boolean strictMode;

    @Value("${security.junit_relaxed_mode:true}")
    private boolean junitRelaxedMode;

    private final UserService userService;
    private final Queue<CompletableFuture<String>> executionQueue = new LinkedList<>();
    private final Object queueLock = new Object();
    private final ExecutorService queueProcessor = Executors.newSingleThreadExecutor();
    private Semaphore resourceSemaphore;
    private volatile boolean processingQueue = false;

    private final Logger logger = LoggerFactory.getLogger(CodeExecutionService.class);

    private final MaliciousCodeDetector codeDetector = new MaliciousCodeDetector();

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
        logger.info("Security scanning {}", securityEnabled ? "ENABLED" : "DISABLED");
        logger.info("Strict mode {}", strictMode ? "ENABLED" : "DISABLED");
        logger.info("JUnit relaxed mode {}", junitRelaxedMode ? "ENABLED" : "DISABLED");
    }

    /**
     * Initialize the semaphore based on available system resources.
     */
    private void initializeResourceSemaphore() {
        int systemCores = Runtime.getRuntime().availableProcessors();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();

        long availableMemory = maxMemory - totalMemory + Runtime.getRuntime().freeMemory();

        int maxByCpu = Math.max(1, systemCores / 2);
        long containerMemoryBytes = parseMemoryLimit(memoryLimit);
        int maxByMemory = Math.max(1, (int) (availableMemory / containerMemoryBytes));

        int maxConcurrentExecutions = Math.min(maxByCpu * 2, maxByMemory);

        maxConcurrentExecutions = Math.min(16, maxConcurrentExecutions);

        this.resourceSemaphore = new Semaphore(maxConcurrentExecutions);

        logger.info("Initialized CodeExecutionService with max {} concurrent executions", maxConcurrentExecutions);
        logger.info("System cores: {}, Available memory: {}MB", systemCores, availableMemory / 1024 / 1024);
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
                logger.warn("Invalid memory limit format: {}, using default 128MB", memoryLimit);
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
        return executeCode(code, false, null);
    }

    /**
     * Execute JUnit tests in a Docker container with security scanning
     *
     * @param studentCode The student's Java code
     * @param junitTests  The JUnit test code
     * @return The test execution results as JSON string
     * @throws Exception if execution fails
     */
    public String executeJUnitTests(String studentCode, String junitTests) throws Exception {
        return executeCode(null, true, Map.of("studentCode", studentCode, "testCode", junitTests));
    }

    /**
     * Internal method to execute code with optional JUnit test mode
     */
    private String executeCode(String code, boolean isJUnitTest, Map<String, String> testData) throws Exception {
        if (securityEnabled) {
            if (isJUnitTest) {
                // Separate analysis for student code and test code
                String studentCode = testData.get("studentCode");
                String testCode = testData.get("testCode");

                // Always apply strict security to student code
                MaliciousCodeDetector.MaliciousCodeResult studentSecurityResult =
                        codeDetector.analyzeCode(studentCode, false); // false = strict mode for student code

                if (studentSecurityResult.malicious()) {
                    String securityMessage = formatSecurityMessage(studentSecurityResult, "Student Code");

                    logger.warn("SECURITY VIOLATION - User: {} - Risk Level: {} - Reasons: {} - Type: Student Code in JUnit Test", getCurrentUserInfo(), studentSecurityResult.riskLevel(), studentSecurityResult.reasons());

                    if (strictMode || studentSecurityResult.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                        return formatTestErrorResult(securityMessage);
                    }
                }

                // Apply relaxed security to test code if enabled
                if (junitRelaxedMode) {
                    MaliciousCodeDetector.MaliciousCodeResult testSecurityResult =
                            codeDetector.analyzeCode(testCode, true); // true = relaxed mode for test code

                    // Only block test code for CRITICAL violations
                    if (testSecurityResult.malicious() && testSecurityResult.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                        String securityMessage = formatSecurityMessage(testSecurityResult, "Test Code");

                        logger.warn("CRITICAL SECURITY VIOLATION - User: {} - Risk Level: {} - Reasons: {} - Type: Test Code", getCurrentUserInfo(), testSecurityResult.riskLevel(), testSecurityResult.reasons());

                        return formatTestErrorResult(securityMessage);
                    } else if (testSecurityResult.malicious()) {
                        logger.warn("WARNING: JUnit test code has suspicious patterns but execution allowed in relaxed mode - {}", testSecurityResult.reasons());
                    }
                } else {
                    // Apply strict mode to test code as well
                    MaliciousCodeDetector.MaliciousCodeResult testSecurityResult =
                            codeDetector.analyzeCode(testCode, false);

                    if (testSecurityResult.malicious()) {
                        String securityMessage = formatSecurityMessage(testSecurityResult, "Test Code");

                        logger.warn("SECURITY VIOLATION - User: {} - Risk Level: {} - Reasons: {} - Type: Test Code (Strict Mode)", getCurrentUserInfo(), testSecurityResult.riskLevel(), testSecurityResult.reasons());

                        if (strictMode || testSecurityResult.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                            return formatTestErrorResult(securityMessage);
                        }
                    }
                }
            } else {
                // Regular code execution - always strict
                MaliciousCodeDetector.MaliciousCodeResult securityResult = codeDetector.analyzeCode(code, false);

                if (securityResult.malicious()) {
                    String securityMessage = formatSecurityMessage(securityResult, "Code");

                    logger.warn("SECURITY VIOLATION - User: {} - Risk Level: {} - Reasons: {} - Type: Regular Code Execution", getCurrentUserInfo(), securityResult.riskLevel(), securityResult.reasons());

                    if (strictMode || securityResult.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                        return securityMessage;
                    }

                    logger.warn("WARNING: Potentially risky code detected but execution allowed in non-strict mode");
                }
            }
        }

        if (!dockerEnabled) {
            return isJUnitTest ? simulateTestExecution(testData) : simulateExecution(code);
        }

        if (resourceSemaphore.tryAcquire()) {
            try {
                return isJUnitTest ? executeJUnitInDocker(testData) : executeInDocker(code);
            } finally {
                resourceSemaphore.release();
                processNextInQueue();
            }
        } else {
            userService.setCurrentExecutions(true);
            return addToQueueAndWait(code, isJUnitTest, testData);
        }
    }

    private String formatSecurityMessage(MaliciousCodeDetector.MaliciousCodeResult result, String codeType) {
        StringBuilder message = new StringBuilder();
        message.append("🛡️ SECURITY ALERT: ").append(codeType).append(" execution blocked\n\n");
        message.append("Risk Level: ").append(result.riskLevel()).append("\n");
        message.append("Detected Issues:\n");

        String[] reasons = result.reasons().split(";");
        for (String reason : reasons) {
            if (!reason.trim().isEmpty()) {
                message.append("• ").append(reason.trim()).append("\n");
            }
        }

        message.append("\n📋 Security Guidelines:\n");
        if ("Student Code".equals(codeType)) {
            message.append("• Use only standard Java APIs for assignments\n");
            message.append("• Avoid system calls, file operations, and network access\n");
            message.append("• Focus on algorithm implementation and data structures\n");
        } else {
            message.append("• JUnit tests should focus on testing functionality\n");
            message.append("• Avoid system manipulation and dangerous operations\n");
        }
        message.append("• Contact instructor if you believe this is a false positive\n");

        return message.toString();
    }

    private String formatTestErrorResult(String errorMessage) {
        return String.format("""
                {
                    "success": false,
                    "message": "%s",
                    "testsSucceeded": 0,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "totalTests": 0,
                    "allTestsPassed": false
                }
                """, errorMessage.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    private String getCurrentUserInfo() {
        try {
            return userService != null ? "UserService available" : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String addToQueueAndWait(String code, boolean isJUnitTest, Map<String, String> testData) {
        CompletableFuture<String> queuedTask = CompletableFuture.supplyAsync(() -> {
            try {
                resourceSemaphore.acquire();
                try {
                    return isJUnitTest ? executeJUnitInDocker(testData) : executeInDocker(code);
                } finally {
                    resourceSemaphore.release();
                    processNextInQueue();
                }
            } catch (Exception e) {
                return isJUnitTest ? formatTestErrorResult("ERROR: " + e.getMessage()) : "ERROR: " + e.getMessage();
            }
        });

        synchronized (queueLock) {
            executionQueue.offer(queuedTask);
        }

        try {
            int timeout = isJUnitTest ? testTimeoutSeconds * 2 : timeoutSeconds * 2;
            return queuedTask.get(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMsg = "ERROR: Execution timed out or failed: " + e.getMessage();
            return isJUnitTest ? formatTestErrorResult(errorMsg) : errorMsg;
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
            Process process = getExecutionProcess(code, containerId, false);

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "Execution timeout - your code took too long to run";
            }

            return processExecutionResult(process);
        } catch (Exception e) {
            cleanupContainer(containerId);
            throw e;
        }
    }

    /**
     * Execute JUnit tests in a Docker container for security
     */
    private String executeJUnitInDocker(Map<String, String> testData) throws Exception {
        String containerId = "codedrill-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            Process process = getJUnitProcess(testData, containerId);

            boolean completed = process.waitFor(testTimeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return formatTestErrorResult("Test execution timeout - tests took too long to run");
            }

            return processJUnitResult(process);
        } catch (Exception e) {
            cleanupContainer(containerId);
            throw e;
        }
    }

    private Process getExecutionProcess(String code, String containerId, boolean isTest) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--name", containerId,
                "--rm", "-i",
                networkDisabled ? "--network=none" : "",
                "--memory=" + (isTest ? testMemoryLimit : memoryLimit),
                "--cpus=" + (isTest ? testCpuLimit : cpuLimit),
                "--ulimit", "nproc=" + (isTest ? testProcessLimit : processLimit) + ":" + (isTest ? testProcessLimit * 2 : processLimit * 2),
                dockerImage
        );

        pb.command().removeIf(String::isEmpty);
        Process process = pb.start();

        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write(code);
        }
        return process;
    }

    private Process getJUnitProcess(Map<String, String> testData, String containerId) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--name", containerId,
                "--rm", "-i",
                networkDisabled ? "--network=none" : "",
                "--memory=" + testMemoryLimit,
                "--cpus=" + testCpuLimit,
                "--ulimit", "nproc=" + testProcessLimit + ":" + (testProcessLimit * 2),
                junitDockerImage
        );

        pb.command().removeIf(String::isEmpty);
        Process process = pb.start();

        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write("===STUDENT_CODE===\n");
            writer.write(testData.get("studentCode"));
            writer.write("\n===TEST_CODE===\n");
            writer.write(testData.get("testCode"));
            writer.write("\n===END===\n");
        }
        return process;
    }

    private String processExecutionResult(Process process) throws IOException {
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
    }

    private String processJUnitResult(Process process) throws IOException {
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

        String result = output.toString().trim();

        // If there are errors but no output, format as error result
        if (result.isEmpty() && !errors.isEmpty()) {
            return formatTestErrorResult("Test execution failed: " + extractMainError(errors.toString()));
        }

        // If output doesn't look like JSON, wrap it in an error result
        if (!result.startsWith("{")) {
            return formatTestErrorResult("Unexpected test output: " + result);
        }

        return result;
    }

    private void cleanupContainer(String containerId) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerId).start();
        } catch (Exception cleanupEx) {
            // Ignore cleanup errors
        }
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

    private String simulateTestExecution(Map<String, String> testData) {
        return """
                {
                    "success": true,
                    "message": "Simulated test execution (Docker disabled)",
                    "testsSucceeded": 1,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "totalTests": 1,
                    "allTestsPassed": true
                }
                """;
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
     * Enhanced malicious code detector with proper JUnit test support
     */
    private static class MaliciousCodeDetector {

        // JUnit-specific allowed patterns (when in relaxed mode)
        private static final Set<String> JUNIT_ALLOWED_OPERATIONS = new HashSet<>(Arrays.asList(
                "system.out", "system.err", "system.setin", "system.setout", "system.seterr",
                "bytearrayoutputstream", "printstream", "bytearrayinputstream",
                "assertequals", "asserttrue", "assertfalse", "assertthrows", "assertnotequals",
                "beforeeach", "aftereach", "beforeall", "afterall", "test", "junit",
                "mockito", "mock", "verify", "when", "thenreturn", "capture",
                "stringwriter", "printwriter", "bufferedreader", "stringreader"
        ));

        // Container escape and privilege escalation patterns
        private static final Pattern CONTAINER_ESCAPE_PATTERN = Pattern.compile(
                "(?i)(?:/proc/|/sys/|/dev/|/host|/var/run/docker\\.sock|" +
                        "chroot|pivot_root|unshare|nsenter|capsh|" +
                        "docker|kubernetes|k8s|container|cgroup)",
                Pattern.CASE_INSENSITIVE
        );

        // System execution - FIXED to exclude JUnit testing operations
        private static final Pattern SYSTEM_EXEC_PATTERN = Pattern.compile(
                "(?i)(?<!system\\.)runtime\\.getruntime\\(\\)\\.exec|" +
                        "(?<!system\\.)processbuilder|" +
                        "new\\s+process(?:builder)?|" +
                        "(?<!system\\.)exec|" +
                        "bash|sh|cmd|powershell|/bin/|" +
                        "\\bexec\\s*\\((?!.*test)|" + // Allow exec in test context
                        "(?<!\\.)system\\s*\\(", // system() calls but not System. methods
                Pattern.CASE_INSENSITIVE
        );

        // File system attacks specific to containers - UPDATED to exclude JUnit I/O
        private static final Pattern DANGEROUS_FILE_PATTERN = Pattern.compile(
                "(?i)(?:file(?:writer|outputstream|inputstream|reader)|" +
                        "randomaccessfile|nio\\.file\\.files|" +
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

        // Reflection and dynamic code loading - UPDATED to allow JUnit reflection
        private static final Pattern REFLECTION_PATTERN = Pattern.compile(
                "(?i)class\\.forname(?!.*test)|" +
                        "getdeclaredmethod(?!.*test)|" +
                        "setaccessible(?!.*test)|" +
                        "method\\.invoke(?!.*test)|" +
                        "field\\.set(?!.*test)|" +
                        "constructor\\.newinstance(?!.*test)|" +
                        "unsafe|sun\\.misc|methodhandle",
                Pattern.CASE_INSENSITIVE
        );

        // JVM and system manipulation - UPDATED to exclude legitimate System methods
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
         * Enhanced malicious code analysis with relaxed mode for JUnit tests
         *
         * @param code               The code to analyze
         * @param isJUnitRelaxedMode Whether to apply relaxed checking for JUnit test code
         */
        public MaliciousCodeResult analyzeCode(String code, boolean isJUnitRelaxedMode) {
            if (code == null || code.isEmpty()) {
                return new MaliciousCodeResult(false, RiskLevel.NONE, "No code provided");
            }

            String normalizedCode = normalizeCode(code);
            StringBuilder detectionReasons = new StringBuilder();
            RiskLevel maxRiskLevel = RiskLevel.NONE;

            // Pre-analyze to determine if this looks like JUnit test code
            boolean looksLikeJUnitTest = isJUnitRelaxedMode && isLikelyJUnitCode(normalizedCode);

            // Always check critical security patterns regardless of mode
            maxRiskLevel = checkAndUpdate(CONTAINER_ESCAPE_PATTERN, normalizedCode,
                    "Container escape attempt detected", RiskLevel.CRITICAL, maxRiskLevel, detectionReasons);

            // Check system execution with JUnit awareness
            if (looksLikeJUnitTest) {
                // For JUnit code, use more lenient system execution checking
                maxRiskLevel = checkJUnitAwareSystemExecution(normalizedCode, maxRiskLevel, detectionReasons);
            } else {
                maxRiskLevel = checkAndUpdate(SYSTEM_EXEC_PATTERN, normalizedCode,
                        "System command execution detected", RiskLevel.CRITICAL, maxRiskLevel, detectionReasons);
            }

            maxRiskLevel = checkAndUpdate(BYTECODE_PATTERN, normalizedCode,
                    "Bytecode manipulation detected", RiskLevel.HIGH, maxRiskLevel, detectionReasons);

            maxRiskLevel = checkAndUpdate(SERIALIZATION_PATTERN, normalizedCode,
                    "Serialization attack patterns detected", RiskLevel.HIGH, maxRiskLevel, detectionReasons);

            // Apply different checking based on mode
            if (isJUnitRelaxedMode && looksLikeJUnitTest) {
                // Relaxed mode for JUnit tests - only flag truly dangerous operations
                maxRiskLevel = checkJUnitSpecificPatterns(normalizedCode, maxRiskLevel, detectionReasons);
            } else {
                // Strict mode for student code
                maxRiskLevel = checkAndUpdate(JVM_MANIPULATION_PATTERN, normalizedCode,
                        "JVM manipulation detected", RiskLevel.HIGH, maxRiskLevel, detectionReasons);

                maxRiskLevel = checkAndUpdate(DANGEROUS_FILE_PATTERN, normalizedCode,
                        "Dangerous file operations detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

                maxRiskLevel = checkAndUpdate(NETWORK_PATTERN, normalizedCode,
                        "Network operations detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

                maxRiskLevel = checkAndUpdate(REFLECTION_PATTERN, normalizedCode,
                        "Reflection API abuse detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

                maxRiskLevel = checkAndUpdate(RESOURCE_ATTACK_PATTERN, normalizedCode,
                        "Resource exhaustion patterns detected", RiskLevel.MEDIUM, maxRiskLevel, detectionReasons);

                // Check forbidden keywords in strict mode
                String lowerCode = normalizedCode.toLowerCase();
                for (String keyword : FORBIDDEN_KEYWORDS) {
                    if (lowerCode.contains(keyword.toLowerCase())) {
                        maxRiskLevel = updateRiskLevel(RiskLevel.HIGH, maxRiskLevel);
                        detectionReasons.append("Forbidden keyword detected: ").append(keyword).append("; ");
                    }
                }

                maxRiskLevel = performHeuristicChecks(normalizedCode, maxRiskLevel, detectionReasons);
            }

            boolean isMalicious = maxRiskLevel.ordinal() >= RiskLevel.MEDIUM.ordinal();
            return new MaliciousCodeResult(isMalicious, maxRiskLevel, detectionReasons.toString());
        }

        /**
         * Check if code likely contains JUnit test patterns
         */
        private boolean isLikelyJUnitCode(String code) {
            String lowerCode = code.toLowerCase();
            int junitIndicators = 0;

            // Count JUnit-specific indicators
            if (lowerCode.contains("@test")) junitIndicators++;
            if (lowerCode.contains("@beforeeach")) junitIndicators++;
            if (lowerCode.contains("@aftereach")) junitIndicators++;
            if (lowerCode.contains("assertequals")) junitIndicators++;
            if (lowerCode.contains("asserttrue")) junitIndicators++;
            if (lowerCode.contains("assertfalse")) junitIndicators++;
            if (lowerCode.contains("junit")) junitIndicators++;
            if (lowerCode.contains("test") && lowerCode.contains("class")) junitIndicators++;
            if (lowerCode.contains("bytearrayoutputstream")) junitIndicators++;
            if (lowerCode.contains("system.setout")) junitIndicators++;

            // If we have 2 or more JUnit indicators, treat as JUnit code
            return junitIndicators >= 2;
        }

        /**
         * JUnit-aware system execution checking
         */
        private RiskLevel checkJUnitAwareSystemExecution(String code, RiskLevel currentMax, StringBuilder reasons) {
            String lowerCode = code.toLowerCase();

            // Check for dangerous system execution patterns that are NOT JUnit-related
            if (code.matches("(?i).*runtime\\.getruntime\\(\\)\\.exec.*") ||
                    code.matches("(?i).*processbuilder.*") ||
                    code.matches("(?i).*\\bbash\\b.*") ||
                    code.matches("(?i).*\\bsh\\b.*") ||
                    code.matches("(?i).*\\bcmd\\b.*") ||
                    code.matches("(?i).*\\bpowershell\\b.*")) {

                // These are truly dangerous - flag them
                reasons.append("Dangerous system command execution detected; ");
                return updateRiskLevel(RiskLevel.CRITICAL, currentMax);
            }

            // System.setOut, System.setErr are allowed in JUnit tests
            if (lowerCode.contains("system.setout") ||
                    lowerCode.contains("system.seterr") ||
                    lowerCode.contains("system.setin")) {
                // These are legitimate JUnit operations - don't flag
                return currentMax;
            }

            return currentMax;
        }

        /**
         * JUnit-specific pattern checking with relaxed rules
         */
        private RiskLevel checkJUnitSpecificPatterns(String code, RiskLevel currentMax, StringBuilder reasons) {
            RiskLevel maxLevel = currentMax;
            String lowerCode = code.toLowerCase();

            // Check dangerous file operations - allow test-related I/O
            if (DANGEROUS_FILE_PATTERN.matcher(code).find()) {
                // Allow JUnit testing I/O operations
                if (!containsAnyJUnitPattern(lowerCode, "stringwriter", "printwriter", "stringreader",
                        "bytearrayoutputstream", "bytearrayinputstream", "test")) {
                    maxLevel = updateRiskLevel(RiskLevel.MEDIUM, maxLevel);
                    reasons.append("Non-test file operations detected; ");
                }
            }

            // Network operations still not allowed
            if (NETWORK_PATTERN.matcher(code).find()) {
                maxLevel = updateRiskLevel(RiskLevel.MEDIUM, maxLevel);
                reasons.append("Network operations detected; ");
            }

            // Allow reflection only for JUnit-related operations
            if (REFLECTION_PATTERN.matcher(code).find()) {
                if (!containsAnyJUnitPattern(lowerCode, "test", "junit", "assertequals", "invoke", "mock")) {
                    maxLevel = updateRiskLevel(RiskLevel.MEDIUM, maxLevel);
                    reasons.append("Non-JUnit reflection detected; ");
                }
            }

            // Check for forbidden keywords (still critical even in relaxed mode)
            for (String keyword : FORBIDDEN_KEYWORDS) {
                if (lowerCode.contains(keyword.toLowerCase())) {
                    maxLevel = updateRiskLevel(RiskLevel.HIGH, maxLevel);
                    reasons.append("Forbidden keyword in test: ").append(keyword).append("; ");
                }
            }

            return maxLevel;
        }

        /**
         * Check if code contains any JUnit-allowed patterns
         */
        private boolean containsAnyJUnitPattern(String lowerCode, String... patterns) {
            for (String pattern : patterns) {
                if (lowerCode.contains(pattern.toLowerCase())) {
                    return true;
                }
            }
            return false;
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

        // Backward compatibility method
        public MaliciousCodeResult analyzeCode(String code) {
            return analyzeCode(code, false); // Default to strict mode
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