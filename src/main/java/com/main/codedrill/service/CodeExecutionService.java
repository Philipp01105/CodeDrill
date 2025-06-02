package com.main.codedrill.service;

import jakarta.annotation.PostConstruct;
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

    private final UserService userService;
    private final Queue<CompletableFuture<String>> executionQueue = new LinkedList<>();
    private final Object queueLock = new Object();
    private final ExecutorService queueProcessor = Executors.newSingleThreadExecutor();
    private Semaphore resourceSemaphore;
    private volatile boolean processingQueue = false;

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

        int maxConcurrentExecutions = Math.min(maxByCpu, maxByMemory);

        // Ensure we have at least 1 concurrent execution and not more than 16
        maxConcurrentExecutions = Math.min(16, maxConcurrentExecutions);

        this.resourceSemaphore = new Semaphore(maxConcurrentExecutions);

        System.out.println("Initialized CodeExecutionService with max " + maxConcurrentExecutions + " concurrent executions");
        System.out.println("System cores: " + systemCores + ", Available memory: " + (availableMemory / 1024 / 1024) + "MB");
    }

    private long parseMemoryLimit(String memoryLimit) {
        if (memoryLimit == null || memoryLimit.trim().isEmpty()) {
            return 128 * 1024 * 1024; // Default to 128MB
        }

        String limit = memoryLimit.toLowerCase().trim();

        if (limit.endsWith("m")) {
            return Long.parseLong(limit.replace("m", "")) * 1024 * 1024;
        } else if (limit.endsWith("g")) {
            return Long.parseLong(limit.replace("g", "")) * 1024 * 1024 * 1024;
        } else if (limit.endsWith("k")) {
            return Long.parseLong(limit.replace("k", "")) * 1024;
        } else {
            // Assume bytes if no unit specified
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
            while (!Thread.currentThread().isInterrupted()) {
                CompletableFuture<String> task = null;

                synchronized (queueLock) {
                    if (!executionQueue.isEmpty()) {
                        task = executionQueue.poll();
                    }
                }

                if (task != null) {
                    // Process the task (this will be handled by the CompletableFuture logic)
                    processingQueue = true;
                } else {
                    processingQueue = false;
                    try {
                        Thread.sleep(100); // Wait 100ms before checking again
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    /**
     * Execute Java code in a Docker container
     *
     * @param code The Java code to execute
     * @return The execution output
     * @throws Exception if execution fails
     */
    public String executeJavaCode(String code) throws Exception {
        if (!dockerEnabled) {
            return simulateExecution(code);
        }

        // Try to acquire a resource slot immediately
        if (resourceSemaphore.tryAcquire()) {
            try {
                return executeInDocker(code);
            } finally {
                resourceSemaphore.release();
                processNextInQueue(); // Process next task in queue if any
            }
        } else {
            // Add to queue if resources are unavailable
            return addToQueueAndWait(code);
        }
    }

    private String addToQueueAndWait(String code) {
        CompletableFuture<String> future = new CompletableFuture<>();

        // Add a task to the queue that will execute when resources become available
        CompletableFuture<String> queuedTask = CompletableFuture.supplyAsync(() -> {
            try {
                // Wait for resource availability
                resourceSemaphore.acquire();
                try {
                    return executeInDocker(code);
                } finally {
                    resourceSemaphore.release();
                    processNextInQueue(); // Process next task in queue if any
                }
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        });

        synchronized (queueLock) {
            executionQueue.offer(queuedTask);
        }

        // Return the result when it's ready (with timeout)
        try {
            return queuedTask.get(timeoutSeconds * 2L, TimeUnit.SECONDS); // Allow double timeout for queued tasks
        } catch (Exception e) {
            return "ERROR: Execution timed out or failed: " + e.getMessage();
        }
    }

    private void processNextInQueue() {
        synchronized (queueLock) {
            if (!executionQueue.isEmpty() && !processingQueue) {
                // Let the queue processor handle the next task
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
            // Cleanup container in case of error
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

        // Remove empty arguments
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
                    } catch (Exception e) {
                        // Ignore parsing errors
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
    public void destroy() {
        if (queueProcessor != null && !queueProcessor.isShutdown()) {
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
}