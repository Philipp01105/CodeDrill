package com.main.codedrill.controller;

import com.main.codedrill.model.*;
import com.main.codedrill.repository.LearningPathRepository;
import com.main.codedrill.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final TaskService taskService;
    private final CodeExecutionService codeExecutionService;
    private final UserService userService;
    private final TaskCompletionService taskCompletionService;
    private final JUnitTestService junitTestService;
    private final GamificationService gamificationService;
    private final LearningPathRepository learningPathRepository;
    private final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    public ApiController(TaskService taskService,
                         CodeExecutionService codeExecutionService,
                         UserService userService,
                         TaskCompletionService taskCompletionService,
                         JUnitTestService junitTestService,
                         GamificationService gamificationService,
                         LearningPathRepository learningPathRepository) {
        this.taskService = taskService;
        this.codeExecutionService = codeExecutionService;
        this.userService = userService;
        this.taskCompletionService = taskCompletionService;
        this.junitTestService = junitTestService;
        this.gamificationService = gamificationService;
        this.learningPathRepository = learningPathRepository;
    }

    // Task API endpoints
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Task> getTask(@PathVariable Long taskId) {
        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    // Code Runner endpoints (moved from CodeRunnerController)
    @PostMapping("/code-runner/run/{taskId}")
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

            // Check for security blocks or rate limits
            if (executionOutput.startsWith("️🛡️ SECURITY ALERT: Code execution blocked")) {
                response.put("success", false);
                response.put("output", executionOutput);
                response.put("message", "Code execution blocked for security reasons");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            }

            boolean outputCorrect;
            boolean testsPass;

            // Check expected output
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

            // Run JUnit tests if available
            Map<String, Object> testResults = new HashMap<>();
            if (task.getJunitTests() != null && !task.getJunitTests().trim().isEmpty()) {
                testResults = junitTestService.runTests(code, task.getJunitTests());
                logger.info("Test results: {}", testResults.values());
                testsPass = (boolean) testResults.getOrDefault("allTestsPassed", true);
                response.put("testResults", testResults);
            } else {
                testsPass = true;
                testResults.put("message", "No JUnit tests defined");
                response.put("testResults", testResults);
            }

            boolean correct = outputCorrect && testsPass;

            // Handle task completion and gamification
            if (correct) {
                User user = userService.findByUsername(auth.getName());
                UserTaskCompletion completion = taskCompletionService.markTaskAsCompleted(user, task);
                boolean isFirstCompletion = completion != null;

                response.put("taskCompleted", isFirstCompletion);

                if (isFirstCompletion) {
                    // Process gamification rewards
                    Map<String, Object> gamificationResult = gamificationService.processTaskCompletion(user, task, true);

                    // Add gamification data to response
                    if (gamificationResult.containsKey("xpEarned")) {
                        response.put("xpEarned", gamificationResult.get("xpEarned"));
                    }

                    if (gamificationResult.containsKey("newAchievements")) {
                        response.put("newAchievements", gamificationResult.get("newAchievements"));
                    }

                    if (gamificationResult.containsKey("updatedStats")) {
                        response.put("updatedStats", gamificationResult.get("updatedStats"));
                    }

                    if (gamificationResult.containsKey("updatedLearningPath")) {
                        response.put("updatedLearningPath", gamificationResult.get("updatedLearningPath"));
                    }
                } else {
                    response.put("message", "Task already completed - no additional XP awarded");
                }
            }

            response.put("success", true);
            response.put("output", executionOutput);
            response.put("correct", correct);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error executing code for task {}: {}", taskId, e.getMessage(), e);
            response.put("success", false);
            response.put("output", "Error executing code: " + e.getMessage());
            response.put("correct", false);
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/code-runner/status")
    public ResponseEntity<Map<String, Object>> getExecutionStatus() {
        boolean inQueue = userService.getCurrentExecutions();

        Map<String, Object> status = new HashMap<>();
        status.put("current", inQueue);
        status.put("message", inQueue ? "Execution queue is busy" : "Execution slots available");

        return ResponseEntity.ok(status);
    }

    @GetMapping("/code-runner/completions")
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

    // Gamification API endpoints
    @GetMapping("/user/stats")
    public ResponseEntity<UserStats> getUserStats() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(auth.getName());
        UserStats stats = gamificationService.getUserStats(user);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/user/achievements")
    public ResponseEntity<List<Map<String, Object>>> getUserAchievements() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(auth.getName());
        List<Map<String, Object>> achievements = gamificationService.getUserAchievements(user);

        return ResponseEntity.ok(achievements);
    }

    @GetMapping("/user/learning-path")
    public ResponseEntity<LearningPath> getUserLearningPath() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(auth.getName());
        LearningPath learningPath = gamificationService.getLearningPath(user);

        return ResponseEntity.ok(learningPath);
    }

    @PostMapping("/user/learning-path")
    public ResponseEntity<Map<String, Object>> updateLearningPath(@RequestBody Map<String, String> payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String pathType = payload.get("pathType");
        if (pathType == null || pathType.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            User user = userService.findByUsername(auth.getName());
            LearningPath learningPath = gamificationService.getLearningPath(user);
            learningPath.setPathType(pathType.toUpperCase());

            learningPathRepository.save(learningPath);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("pathType", learningPath.getPathType());
            response.put("message", "Learning path updated successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating learning path: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to update learning path");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<Map<String, Object>>> getWeeklyLeaderboard() {
        List<Map<String, Object>> leaderboard = gamificationService.getWeeklyLeaderboard();
        return ResponseEntity.ok(leaderboard);
    }

    @GetMapping("/leaderboard/weekly")
    public ResponseEntity<List<Map<String, Object>>> getWeeklyLeaderboardWithCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<Map<String, Object>> leaderboard = gamificationService.getWeeklyLeaderboard();

        // Mark current user if authenticated
        if (auth != null && !auth.getName().equals("anonymousUser")) {
            String currentUsername = auth.getName();
            for (int i = 0; i < leaderboard.size(); i++) {
                Map<String, Object> entry = leaderboard.get(i);
                if (currentUsername.equals(entry.get("username"))) {
                    entry.put("currentUser", true);
                    entry.put("rank", i + 1);
                    break;
                }
            }
        }

        return ResponseEntity.ok(leaderboard);
    }

    // Analytics endpoints
    @PostMapping("/analytics/track/view/{taskId}")
    public ResponseEntity<String> trackTaskView(@PathVariable Long taskId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.ok("Not authenticated");
        }

        try {
            User user = userService.findByUsername(auth.getName());
            Task task = taskService.getTaskById(taskId);

            if (user != null && task != null) {
                // Track view (you might want to implement this in your analytics service)
                logger.info("User {} viewed task {}", user.getUsername(), task.getTitle());
                return ResponseEntity.ok("Success");
            }

            return ResponseEntity.ok("Failed - user or task not found");

        } catch (Exception e) {
            logger.error("Error tracking task view: {}", e.getMessage());
            return ResponseEntity.ok("Failed");
        }
    }

    @PostMapping("/analytics/track/attempt/{taskId}")
    public ResponseEntity<String> trackTaskAttempt(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> payload) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.ok("Not authenticated");
        }

        try {
            User user = userService.findByUsername(auth.getName());
            Task task = taskService.getTaskById(taskId);

            if (user != null && task != null) {
                boolean successful = (Boolean) payload.getOrDefault("successful", false);
                String errorMessage = (String) payload.getOrDefault("errorMessage", "");
                String code = (String) payload.getOrDefault("code", "");

                // Track attempt (implement in your analytics service)
                logger.info("User {} attempted task {} - Success: {}",
                        user.getUsername(), task.getTitle(), successful);

                return ResponseEntity.ok("Success");
            }

            return ResponseEntity.ok("Failed - user or task not found");

        } catch (Exception e) {
            logger.error("Error tracking task attempt: {}", e.getMessage());
            return ResponseEntity.ok("Failed");
        }
    }

    // Health check endpoint
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "OK");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "CodeDrill API");

        return ResponseEntity.ok(health);
    }
}