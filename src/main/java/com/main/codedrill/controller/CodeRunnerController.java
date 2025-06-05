package com.main.codedrill.controller;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.model.UserTaskCompletion;
import com.main.codedrill.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Logger logger = LoggerFactory.getLogger(CodeRunnerController.class);

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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "You must be logged in to use the code runner.");
            response.put("requiresLogin", true);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String code = payload.get("code");

        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();

        try {
            String executionOutput = codeExecutionService.executeJavaCode(code);

            if (executionOutput.startsWith("️🛡️ SECURITY ALERT: Code execution blocked")) {
                response.put("success", false);
                response.put("output", executionOutput);
                response.put("message", "");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            }

            boolean outputCorrect;
            boolean testsPass;

            if (task.getExpectedOutput() != null && !task.getExpectedOutput().trim().isEmpty()) {
                String normalizedExpected = task.getExpectedOutput().trim().replaceAll("\\s+", " ");
                String normalizedActual = executionOutput.trim().replaceAll("\\s+", " ");
                outputCorrect = normalizedExpected.equals(normalizedActual);
                response.put("outputCorrect", outputCorrect);
                response.put("expectedOutput", task.getExpectedOutput());
            } else {
                outputCorrect = true;
                response.put("outputCorrect", true);
                response.put("expectedOutput", "No expected output defined");
            }

            Map<String, Object> testResults = new HashMap<>();
            if (task.getJunitTests() != null && !task.getJunitTests().trim().isEmpty()) {
                testResults = junitTestService.runTests(code, task.getJunitTests());
                logger.info(testResults.values().toString());
                testsPass = (boolean) testResults.getOrDefault("allTestsPassed", true);
                response.put("testResults", testResults);
            } else {
                testsPass = true;
                testResults.put("message", "No JUnit tests defined");
                response.put("testResults", testResults);
            }

            boolean correct = outputCorrect && testsPass;

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
