package com.main.apcsataskwebsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeExecutionService {

    @Value("${docker.timeout_seconds:10}")
    private int timeoutSeconds;
    
    @Value("${docker.image:apcsa-coderunner:latest}")
    private String dockerImage;
    
    @Value("${docker.memory_limit:256m}")
    private String memoryLimit;
    
    @Value("${docker.cpu_limit:0.5}")
    private String cpuLimit;
    
    @Value("${docker.process_limit:32}")
    private int processLimit;
    
    @Value("${docker.network_disabled:true}")
    private boolean networkDisabled;
    
    @Value("${docker.enabled:false}")
    private boolean dockerEnabled;

    @Value("${docker.max_concurrent:8}")
    private int maxConcurrentExecutions;

    private final UserService userService;
    
    @Autowired
    public CodeExecutionService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Execute Java code in a Docker container
     * 
     * @param code The Java code to execute
     * @return The execution output
     * @throws Exception if execution fails
     */
    public String executeJavaCode(String code) throws Exception {
        if (dockerEnabled) {
            // Check if execution slot is available
            if (!userService.acquireExecutionSlot()) {
                return "ERROR: Too many concurrent executions. Please try again later.";
            }
            
            try {
                return executeInDocker(code);
            } finally {
                // Always release the execution slot
                userService.releaseExecutionSlot();
            }
        } else {
            return simulateExecution(code);
        }
    }
    
    /**
     * Execute code in a Docker container for security
     */
    private String executeInDocker(String code) throws Exception {
        // Generate a unique container name
        String containerId = "coderunner-" + UUID.randomUUID().toString().substring(0, 8);
        
        try {
            // Create and start Docker container
            Process process = getProcess(code, containerId);

            // Read output with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "Execution timeout - your code took too long to run";
            }
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Check for errors
            StringBuilder errors = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errors.append(line).append("\n");
                }
            }
            
            if (!errors.isEmpty()) {
                // Extract main error from the stack trace
                return extractMainError(errors.toString());
            }
            
            return output.toString();
        } catch (Exception e) {
            // Ensure container is removed in case of error
            try {
                new ProcessBuilder("docker", "rm", "-f", containerId).start();
            } catch (Exception ex) {
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

        Process process = pb.start();

        // Send code to container stdin
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write(code);
        }
        return process;
    }

    /**
     * Extract the main error message from a Java compilation error
     */
    private String extractMainError(String errorOutput) {
        if (errorOutput.startsWith("Compilation Error")) {
            // Pattern to match the main error message in compilation errors
            Pattern pattern = Pattern.compile("(\\w+\\.java:\\d+: error:.+?(?=\\n\\s*\\^|$))", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(errorOutput);
            
            if (matcher.find()) {
                return "Compilation Error: " + matcher.group(1).trim();
            }
        }
        
        // If we can't extract a specific error or it's a runtime error
        String[] lines = errorOutput.split("\n");
        if (lines.length > 0) {
            // For runtime exceptions, return the first few lines of the stack trace
            StringBuilder result = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                result.append(line).append("\n");
                count++;
                if (count >= 3) break; // Limit to first 3 non-empty lines
            }
            return result.toString();
        }
        
        return errorOutput;
    }
    
    /**
     * Simulate code execution (for development/testing)
     */
    private String simulateExecution(String code) {
        // For demo purposes, just extract output from System.out.print statements
        if (code.contains("System.out.print")) {
            StringBuilder output = new StringBuilder();
            // Super simple simulation - extract content from System.out.print calls
            String[] lines = code.split("\n");
            for (String line : lines) {
                if (line.contains("System.out.print")) {
                    try {
                        int startIndex = line.indexOf("(\"") + 2;
                        int endIndex = line.lastIndexOf("\")");
                        if (startIndex > 1 && endIndex > startIndex) {
                            String content = line.substring(startIndex, endIndex);
                            output.append(content);
                            // Add newline if it's println
                            if (line.contains("println")) {
                                output.append("\n");
                            }
                        }
                    } catch (Exception e) {
                        // Skip malformed print statement
                    }
                }
            }
            if (!output.isEmpty()) {
                return output.toString();
            }
        }
        
        return "Code executed successfully, but no output was detected.";
    }
} 