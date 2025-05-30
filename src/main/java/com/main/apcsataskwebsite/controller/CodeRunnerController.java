package com.main.apcsataskwebsite.controller;

import com.main.apcsataskwebsite.model.Task;
import com.main.apcsataskwebsite.model.User;
import com.main.apcsataskwebsite.model.UserTaskCompletion;
import com.main.apcsataskwebsite.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/code-runner")
public class CodeRunnerController {

    private final TaskService taskService;
    private final CodeExecutionService codeExecutionService;
    private final UserService userService;
    private final TaskCompletionService taskCompletionService;
    private final JUnitTestService junitTestService;

    @Value("${docker.max_concurrent:8}")
    private int maxConcurrentExecutions;

    @Autowired
    public CodeRunnerController(TaskService taskService, 
                              CodeExecutionService codeExecutionService, 
                              UserService userService,
                              TaskCompletionService taskCompletionService,
                              JUnitTestService junitTestService) {
        this.taskService = taskService;
        this.codeExecutionService = codeExecutionService;
        this.userService = userService;
        this.taskCompletionService = taskCompletionService;
        this.junitTestService = junitTestService;
    }

    @PostMapping("/run/{taskId}")
    public ResponseEntity<Map<String, Object>> runCode(
            @PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        // Check if user is authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "You must be logged in to use the code runner.");
            response.put("requiresLogin", true);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String code = payload.get("code");

        // Get the task to retrieve expected output
        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();

        try {
            // Execute code using service (will use Docker if enabled, simulation otherwise)
            String executionOutput = codeExecutionService.executeJavaCode(code);

            // Check if execution was blocked due to concurrent limit
            if (executionOutput.startsWith("ERROR: Too many concurrent executions")) {
                response.put("success", false);
                response.put("output", executionOutput);
                response.put("message", "Too many concurrent code executions. Please try again in a moment.");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            }

            boolean outputCorrect = false;
            boolean testsPass = false;

            // Check output if expected output is provided
            if (task.getExpectedOutput() != null && !task.getExpectedOutput().trim().isEmpty()) {
                // Strip whitespace differences for comparison
                String normalizedExpected = task.getExpectedOutput().trim().replaceAll("\\s+", " ");
                String normalizedActual = executionOutput.trim().replaceAll("\\s+", " ");
                outputCorrect = normalizedExpected.equals(normalizedActual);
                response.put("outputCorrect", outputCorrect);
                response.put("expectedOutput", task.getExpectedOutput());
            } else {
                // If no expected output is provided, consider output check as passed
                outputCorrect = true;
                response.put("outputCorrect", true);
                response.put("expectedOutput", "No expected output defined");
            }

            // Run JUnit tests if available
            Map<String, Object> testResults = new HashMap<>();
            if (task.getJunitTests() != null && !task.getJunitTests().trim().isEmpty()) {
                testResults = junitTestService.runTests(code, task.getJunitTests());
                testsPass = (boolean) testResults.getOrDefault("allTestsPassed", false);
                response.put("testResults", testResults);
            } else {
                // If no tests are provided, consider tests as passed
                testsPass = true;
                testResults.put("message", "No JUnit tests defined");
                response.put("testResults", testResults);
            }

            // Task is correct only if both output is correct and all tests pass
            boolean correct = outputCorrect && testsPass;

            // If correct, mark task as completed
            if (correct) {
                User user = userService.findByUsername(auth.getName());
                UserTaskCompletion completion = taskCompletionService.markTaskAsCompleted(user, task);
                response.put("taskCompleted", completion != null);
            }

            response.put("success", true);
            response.put("output", executionOutput);
            response.put("correct", correct);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("output", "Error executing code: " + e.getMessage());
            response.put("correct", false);
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Boolean> getExecutionStatus() {

        boolean inQueue = userService.getCurrentExecutions();

        return ResponseEntity.ok(inQueue);
    }

    @GetMapping("/completions")
    public ResponseEntity<Map<String, Object>> getUserCompletions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(auth.getName());
        List<Long> completedTaskIds = taskCompletionService.getCompletedTaskIds(user);

        Map<String, Object> response = new HashMap<>();
        response.put("completedTaskIds", completedTaskIds);

        return ResponseEntity.ok(response);
    }

} 
