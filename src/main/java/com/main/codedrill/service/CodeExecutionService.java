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

import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicInteger;


@Service
public class CodeExecutionService {

    // Configuration Properties
    @Value("${docker.timeout_seconds:8}")
    private int timeoutSeconds;

    @Value("${docker.test_timeout_seconds:20}")
    private int testTimeoutSeconds;

    @Value("${docker.image:codedrill:latest}")
    private String dockerImage;

    @Value("${docker.junit_image:codedrill:latest}")
    private String junitDockerImage;

    @Value("${docker.memory_limit:48m}")
    private String memoryLimit;

    @Value("${docker.test_memory_limit:96m}")
    private String testMemoryLimit;

    @Value("${docker.cpu_limit:0.3}")
    private String cpuLimit;

    @Value("${docker.test_cpu_limit:0.6}")
    private String testCpuLimit;

    @Value("${docker.process_limit:6}")
    private int processLimit;

    @Value("${docker.test_process_limit:12}")
    private int testProcessLimit;

    @Value("${docker.network_disabled:true}")
    private boolean networkDisabled;

    @Value("${docker.enabled:true}")
    private boolean dockerEnabled;

    // Security Configuration
    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${security.strict_mode:true}")
    private boolean strictMode;

    @Value("${security.junit_relaxed_mode:true}")
    private boolean junitRelaxedMode;

    // Enhanced Resource Management
    @Value("${execution.max_global_executions:2}")
    private int maxGlobalExecutions;

    @Value("${execution.queue_timeout_seconds:30}")
    private int queueTimeoutSeconds;

    @Value("${execution.cleanup_interval_seconds:30}")
    private int cleanupIntervalSeconds;

    @Value("${execution.container_max_runtime_minutes:5}")
    private int containerMaxRuntimeMinutes;

    // Dependencies and State
    private final UserService userService;
    private final Queue<QueuedTask> executionQueue = new LinkedList<>();
    private final Object queueLock = new Object();
    private final ExecutorService queueProcessor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CodeExecution-QueueProcessor");
        t.setDaemon(true);
        return t;
    });

    private Semaphore globalResourceSemaphore;
    private final AtomicInteger activeExecutions = new AtomicInteger(0);
    private volatile boolean processingQueue = false;
    private volatile boolean shutdownRequested = false;

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
        validateConfiguration();
        initializeResourceSemaphore();
        startQueueProcessor();

        logger.info("🚀 CodeExecutionService initialized");
        logger.info("Security scanning {}", securityEnabled ? "ENABLED" : "DISABLED");
        logger.info("Strict mode {}", strictMode ? "ENABLED" : "DISABLED");
        logger.info("JUnit relaxed mode {}", junitRelaxedMode ? "ENABLED" : "DISABLED");
        logger.info("🔒 Max global executions: {}", maxGlobalExecutions);
    }

    /**
     * Validate configuration on startup
     */
    private void validateConfiguration() {
        if (timeoutSeconds <= 0 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("Invalid timeout: " + timeoutSeconds);
        }
        if (testTimeoutSeconds <= 0 || testTimeoutSeconds > 600) {
            throw new IllegalArgumentException("Invalid test timeout: " + testTimeoutSeconds);
        }
        if (maxGlobalExecutions <= 0 || maxGlobalExecutions > 10) {
            throw new IllegalArgumentException("Invalid max executions: " + maxGlobalExecutions);
        }
        if (memoryLimit == null || memoryLimit.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory limit cannot be empty");
        }

        logger.info("✅ Configuration validated successfully");
    }

    /**
     * Initialize the semaphore based on conservative resource limits
     */
    private void initializeResourceSemaphore() {
        int systemCores = Runtime.getRuntime().availableProcessors();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long availableMemory = maxMemory - totalMemory + Runtime.getRuntime().freeMemory();

        // Conservative approach: limit based on system resources
        int maxByCpu = Math.max(1, systemCores / 4);  // Very conservative
        long containerMemoryBytes = parseMemoryLimit(memoryLimit);
        int maxByMemory = Math.max(1, (int) (availableMemory / (containerMemoryBytes * 4))); // Extra buffer

        // Use the more restrictive limit, but cap at configured max
        int calculatedMax = Math.min(maxByCpu, maxByMemory);
        int finalMax = Math.min(maxGlobalExecutions, calculatedMax);

        this.globalResourceSemaphore = new Semaphore(finalMax);

        logger.info("🔧 Resource limits - System cores: {}, Available memory: {}MB",
                systemCores, availableMemory / 1024 / 1024);
        logger.info("🎯 Final concurrent execution limit: {}", finalMax);
    }

    private long parseMemoryLimit(String memoryLimit) {
        if (memoryLimit == null || memoryLimit.trim().isEmpty()) {
            return 48 * 1024 * 1024;  // Default 48MB
        }

        String limit = memoryLimit.toLowerCase().trim();

        try {
            if (limit.endsWith("m")) {
                return Long.parseLong(limit.replace("m", "")) * 1024 * 1024;
            } else if (limit.endsWith("g")) {
                return Long.parseLong(limit.replace("g", "")) * 1024 * 1024 * 1024;
            } else if (limit.endsWith("k")) {
                return Long.parseLong(limit.replace("k", "")) * 1024;
            } else {
                return Long.parseLong(limit);
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid memory limit format: {}, using default 48MB", memoryLimit);
            return 48 * 1024 * 1024;
        }
    }

    /**
     * Fixed queue processor that actually executes tasks
     */
    private void startQueueProcessor() {
        queueProcessor.submit(() -> {
            logger.info("🏃 Queue processor started");
            boolean wasProcessing = false;

            while (!Thread.currentThread().isInterrupted() && !shutdownRequested) {
                QueuedTask queuedTask = null;

                synchronized (queueLock) {
                    if (!executionQueue.isEmpty()) {
                        queuedTask = executionQueue.poll();
                    }
                }

                if (queuedTask != null) {
                    synchronized (queueLock) {
                        processingQueue = true;
                    }
                    wasProcessing = true;

                    executeQueuedTask(queuedTask);

                } else {
                    boolean previouslyProcessing;
                    synchronized (queueLock) {
                        previouslyProcessing = processingQueue;
                        processingQueue = false;
                    }

                    if (previouslyProcessing && wasProcessing) {
                        try {
                            userService.setCurrentExecutions(false);
                        } catch (Exception e) {
                            logger.warn("Failed to update user service", e);
                        }
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

            logger.info("🛑 Queue processor stopped");
        });
    }

    /**
     * Execute a queued task with proper error handling
     */
    private void executeQueuedTask(QueuedTask queuedTask) {
        try {
            String result;
            if (queuedTask.isJUnitTest()) {
                result = executeJUnitInDocker(queuedTask.getTestData());
            } else {
                result = executeInDocker(queuedTask.getCode());
            }
            queuedTask.getFuture().complete(result);

        } catch (Exception e) {
            logger.error("Task execution failed", e);
            String errorResult = queuedTask.isJUnitTest() ?
                    formatTestErrorResult("Execution failed: " + e.getMessage()) :
                    "ERROR: Execution failed: " + e.getMessage();
            queuedTask.getFuture().complete(errorResult);
        }
    }

    /**
     * Execute Java code with enhanced resource management
     */
    public String executeJavaCode(String code) throws Exception {
        return executeCode(code, false, null);
    }

    /**
     * Execute JUnit tests with enhanced resource management
     */
    public String executeJUnitTests(String studentCode, String junitTests) throws Exception {
        return executeCode(null, true, Map.of("studentCode", studentCode, "testCode", junitTests));
    }

    /**
     * Enhanced code execution with backpressure and proper resource management
     */
    private String executeCode(String code, boolean isJUnitTest, Map<String, String> testData) throws Exception {
        // Security scanning first
        if (securityEnabled) {
            String securityResult = performSecurityAnalysis(code, isJUnitTest, testData);
            if (securityResult != null) {
                return securityResult;
            }
        }

        // Docker disabled simulation
        if (!dockerEnabled) {
            return isJUnitTest ? simulateTestExecution() : simulateExecution(code);
        }

        // Resource acquisition with timeout
        if (!globalResourceSemaphore.tryAcquire(queueTimeoutSeconds, TimeUnit.SECONDS)) {
            String message = String.format(
                    "⏰ System overloaded. Currently processing: %d executions. Please try again in a moment.",
                    activeExecutions.get()
            );
            return isJUnitTest ? formatTestErrorResult(message) : message;
        }

        try {
            activeExecutions.incrementAndGet();
            updateUserServiceCurrentExecution(true);

            // Direct execution if resource available
            return isJUnitTest ? executeJUnitInDocker(testData) : executeInDocker(code);

        } catch (Exception e) {
            logger.error("Direct execution failed", e);
            throw e;
        } finally {
            activeExecutions.decrementAndGet();
            globalResourceSemaphore.release();

            if (activeExecutions.get() == 0) {
                updateUserServiceCurrentExecution(false);
            }
        }
    }

    /**
     * Enhanced security analysis with detailed logging
     */
    private String performSecurityAnalysis(String code, boolean isJUnitTest, Map<String, String> testData) {
        try {
            if (isJUnitTest) {
                String studentCode = testData.get("studentCode");
                String testCode = testData.get("testCode");

                // Always strict for student code
                MaliciousCodeDetector.MaliciousCodeResult studentResult =
                        codeDetector.analyzeCode(studentCode, false);

                if (studentResult.malicious()) {
                    String securityMessage = formatSecurityMessage(studentResult, "Student Code");
                    logger.warn("🚨 SECURITY VIOLATION - User: {} - Risk: {} - Student Code - Reasons: {}",
                            getCurrentUserInfo(), studentResult.riskLevel(), studentResult.reasons());

                    if (strictMode || studentResult.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                        return formatTestErrorResult(securityMessage);
                    }
                }

                // Test code analysis
                MaliciousCodeDetector.MaliciousCodeResult testResult =
                        codeDetector.analyzeCode(testCode, junitRelaxedMode);

                if (testResult.malicious()) {
                    if (!junitRelaxedMode || testResult.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                        String securityMessage = formatSecurityMessage(testResult, "Test Code");
                        logger.warn("🚨 SECURITY VIOLATION - User: {} - Risk: {} - Test Code - Reasons: {}",
                                getCurrentUserInfo(), testResult.riskLevel(), testResult.reasons());
                        return formatTestErrorResult(securityMessage);
                    } else {
                        logger.warn("⚠️ Suspicious test code allowed in relaxed mode: {}", testResult.reasons());
                    }
                }
            } else {
                MaliciousCodeDetector.MaliciousCodeResult result = codeDetector.analyzeCode(code, false);

                if (result.malicious()) {
                    String securityMessage = formatSecurityMessage(result, "Code");
                    logger.warn("🚨 SECURITY VIOLATION - User: {} - Risk: {} - Regular Code - Reasons: {}",
                            getCurrentUserInfo(), result.riskLevel(), result.reasons());

                    if (strictMode || result.riskLevel() == MaliciousCodeDetector.RiskLevel.CRITICAL) {
                        return securityMessage;
                    }

                    logger.warn("⚠️ Risky code allowed in non-strict mode");
                }
            }
        } catch (Exception e) {
            logger.error("Security analysis failed", e);
            return isJUnitTest ?
                    formatTestErrorResult("Security analysis failed: " + e.getMessage()) :
                    "ERROR: Security analysis failed: " + e.getMessage();
        }

        return null; // No security issues
    }

    private void updateUserServiceCurrentExecution(boolean executing) {
        try {
            if (userService != null) {
                userService.setCurrentExecutions(executing);
            }
        } catch (Exception e) {
            logger.warn("Failed to update user service execution status", e);
        }
    }

    /**
     * Enhanced Docker execution with better resource limits
     */
    private String executeInDocker(String code) throws Exception {
        String containerId = "coderunner-" + UUID.randomUUID().toString().substring(0, 8);

        logger.debug("🐳 Starting container: {}", containerId);

        try {
            Process process = getExecutionProcess(code, containerId);

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                logger.warn("⏰ Container {} timed out, force killing", containerId);
                process.destroyForcibly();
                cleanupContainer(containerId);
                return "⏰ Execution timeout - your code took longer than " + timeoutSeconds + " seconds";
            }

            return processExecutionResult(process);

        } catch (Exception e) {
            logger.error("Container execution failed: {}", containerId, e);
            cleanupContainer(containerId);
            throw e;
        } finally {
            cleanupContainer(containerId);
        }
    }

    /**
     * Enhanced JUnit execution with better resource limits
     */
    private String executeJUnitInDocker(Map<String, String> testData) throws Exception {
        String containerId = "codedrill-junit-" + UUID.randomUUID().toString().substring(0, 8);

        logger.debug("🧪 Starting JUnit container: {}", containerId);

        try {
            Process process = getJUnitProcess(testData, containerId);

            boolean completed = process.waitFor(testTimeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                logger.warn("⏰ JUnit container {} timed out, force killing", containerId);
                process.destroyForcibly();
                cleanupContainer(containerId);
                return formatTestErrorResult("⏰ Test execution timeout - tests took longer than " + testTimeoutSeconds + " seconds");
            }

            return processJUnitResult(process);

        } catch (Exception e) {
            logger.error("JUnit container execution failed: {}", containerId, e);
            cleanupContainer(containerId);
            throw e;
        } finally {
            cleanupContainer(containerId);
        }
    }

    private Process getExecutionProcess(String code, String containerId) throws IOException {
        String jvmHeapSize = calculateJvmHeapSize(memoryLimit);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--name", containerId,
                "--rm", "-i",
                networkDisabled ? "--network=none" : "",
                "--memory=" + memoryLimit,
                "--cpus=" + cpuLimit,
                "--ulimit", "nproc=" + processLimit + ":" + processLimit,  // No multiplication
                "--ulimit", "nofile=128:256",  // File descriptor limits
                "--ulimit", "fsize=10000000",  // Max 10MB files
                "--pids-limit=" + (processLimit + 5),  // Additional PID limit
                "-e", "JAVA_OPTS=-Xmx" + jvmHeapSize +
                " -XX:MaxDirectMemorySize=8m" +
                " -XX:MetaspaceSize=16m" +
                " -XX:MaxMetaspaceSize=32m" +
                " -XX:+UseSerialGC" +  // Less memory overhead
                " -XX:TieredStopAtLevel=1",  // Faster startup
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
        String jvmHeapSize = calculateJvmHeapSize(testMemoryLimit);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--name", containerId,
                "--rm", "-i",
                networkDisabled ? "--network=none" : "",
                "--memory=" + testMemoryLimit,
                "--cpus=" + testCpuLimit,
                "--ulimit", "nproc=" + testProcessLimit + ":" + testProcessLimit,
                "--ulimit", "nofile=256:512",
                "--ulimit", "fsize=20000000",  // 20MB for test files
                "--pids-limit=" + (testProcessLimit + 10),
                "-e", "JAVA_OPTS=-Xmx" + jvmHeapSize +
                " -XX:MaxDirectMemorySize=16m" +
                " -XX:MetaspaceSize=32m" +
                " -XX:MaxMetaspaceSize=64m" +
                " -XX:+UseSerialGC" +
                " -XX:TieredStopAtLevel=1",
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

    private String calculateJvmHeapSize(String memoryLimit) {
        long memBytes = parseMemoryLimit(memoryLimit);
        long heapBytes = memBytes * 2 / 3;  // 66% for heap, rest for non-heap
        return Math.max(16, heapBytes / 1024 / 1024) + "m";
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

        return output.toString().trim();
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

        if (result.isEmpty() && !errors.isEmpty()) {
            return formatTestErrorResult("Test execution failed: " + extractMainError(errors.toString()));
        }

        if (!result.startsWith("{")) {
            return formatTestErrorResult("Unexpected test output: " + result);
        }

        return result;
    }

    /**
     * Enhanced container cleanup with force kill
     */
    private void cleanupContainer(String containerId) {
        try {
            // First try graceful stop
            ProcessBuilder stopBuilder = new ProcessBuilder("docker", "stop", containerId);
            Process stopProcess = stopBuilder.start();

            if (!stopProcess.waitFor(3, TimeUnit.SECONDS)) {
                stopProcess.destroyForcibly();
            }

            // Then force remove
            ProcessBuilder rmBuilder = new ProcessBuilder("docker", "rm", "-f", containerId);
            Process rmProcess = rmBuilder.start();

            if (!rmProcess.waitFor(5, TimeUnit.SECONDS)) {
                rmProcess.destroyForcibly();
            }

        } catch (Exception e) {
            logger.warn("Failed to cleanup container: {}", containerId, e);
        }
    }

    /**
     * Scheduled cleanup of orphaned containers
     */
    @Scheduled(fixedRateString = "${execution.cleanup_interval_seconds:30}000")
    public void cleanupOrphanedContainers() {
        try {
            // Remove stopped containers
            ProcessBuilder pruneBuilder = new ProcessBuilder("docker", "container", "prune", "-f");
            Process pruneProcess = pruneBuilder.start();
            pruneProcess.waitFor(10, TimeUnit.SECONDS);

            // Kill long-running codedrill containers
            String killCommand = String.format(
                    "docker ps --filter 'name=coderunner-' --filter 'name=codedrill-' " +
                            "--format '{{.Names}} {{.RunningFor}}' | " +
                            "awk '$2 ~ /%d+ minute/ {print $1}' | " +
                            "xargs -r docker kill",
                    containerMaxRuntimeMinutes
            );

            ProcessBuilder killBuilder = new ProcessBuilder("bash", "-c", killCommand);
            Process killProcess = killBuilder.start();
            killProcess.waitFor(10, TimeUnit.SECONDS);

            logger.debug("🧹 Container cleanup completed");

        } catch (Exception e) {
            logger.warn("Scheduled cleanup failed", e);
        }
    }

    /**
     * Resource monitoring
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void logResourceUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            int runningContainers = getRunningContainerCount();
            int activeExec = activeExecutions.get();
            int availablePermits = globalResourceSemaphore.availablePermits();

            logger.info("📊 RESOURCES - Memory: {}MB used/{}MB total, Containers: {}, Active: {}, Available slots: {}",
                    usedMemory / 1024 / 1024,
                    totalMemory / 1024 / 1024,
                    runningContainers,
                    activeExec,
                    availablePermits);

            if (runningContainers > maxGlobalExecutions * 2) {
                logger.warn("⚠️ HIGH CONTAINER COUNT: {} running (expected max: {})",
                        runningContainers, maxGlobalExecutions);
            }

        } catch (Exception e) {
            logger.debug("Resource monitoring failed", e);
        }
    }

    private int getRunningContainerCount() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "-q",
                    "--filter", "name=coderunner-", "--filter", "name=codedrill-");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            long count = reader.lines().count();
            process.waitFor(5, TimeUnit.SECONDS);

            return (int) count;
        } catch (Exception e) {
            return -1;
        }
    }

    // Helper methods
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
            return userService != null ? "Philipp01105" : "unknown";  // Using your login
        } catch (Exception e) {
            logger.debug("Failed to get user info", e, e);
            return "unknown";
        }
    }

    private String extractMainError(String errorOutput) {
        if (errorOutput.contains("Compilation Error")) {
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
            return result.toString().trim();
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
                        // Ignore parsing errors
                    }
                }
            }
            if (!output.isEmpty()) {
                return output.toString();
            }
        }
        return "✅ Code executed successfully (Docker disabled mode)";
    }

    private String simulateTestExecution() {
        return """
                {
                    "success": true,
                    "message": "✅ Simulated test execution (Docker disabled)",
                    "testsSucceeded": 1,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "totalTests": 1,
                    "allTestsPassed": true
                }
                """;
    }

    /**
     * Graceful shutdown
     */
    @PreDestroy
    public void destroy() {
        logger.info("🛑 Shutting down CodeExecutionService");
        shutdownRequested = true;

        if (!queueProcessor.isShutdown()) {
            queueProcessor.shutdown();
            try {
                if (!queueProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Queue processor didn't terminate gracefully, forcing shutdown");
                    queueProcessor.shutdownNow();
                }
            } catch (InterruptedException e) {
                queueProcessor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Final cleanup
        try {
            cleanupOrphanedContainers();
        } catch (Exception e) {
            logger.warn("Final cleanup failed", e);
        }

        logger.info("✅ CodeExecutionService shutdown complete");
    }

    /**
     * Helper class for queued tasks
     */
    private static class QueuedTask {
        private final String code;
        private final boolean isJUnitTest;
        private final Map<String, String> testData;
        private final CompletableFuture<String> future;

        public QueuedTask(String code, boolean isJUnitTest, Map<String, String> testData, CompletableFuture<String> future) {
            this.code = code;
            this.isJUnitTest = isJUnitTest;
            this.testData = testData;
            this.future = future;
        }

        public String getCode() {
            return code;
        }

        public boolean isJUnitTest() {
            return isJUnitTest;
        }

        public Map<String, String> getTestData() {
            return testData;
        }

        public CompletableFuture<String> getFuture() {
            return future;
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
                if (notContainsAnyJUnitPattern(lowerCode, "stringwriter", "printwriter", "stringreader",
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
                if (notContainsAnyJUnitPattern(lowerCode, "test", "junit", "assertequals", "invoke", "mock")) {
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
        private boolean notContainsAnyJUnitPattern(String lowerCode, String... patterns) {
            for (String pattern : patterns) {
                if (lowerCode.contains(pattern.toLowerCase())) {
                    return false;
                }
            }
            return true;
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