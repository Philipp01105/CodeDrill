package com.main.codedrill.controller;

import com.main.codedrill.model.*;
import com.main.codedrill.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;
    private final GamificationService gamificationService;
    private final AchievementService achievementService;

    @Autowired
    public TaskController(TaskService taskService,
                          UserService userService,
                          GamificationService gamificationService,
                          AchievementService achievementService) {
        this.taskService = taskService;
        this.userService = userService;
        this.gamificationService = gamificationService;
        this.achievementService = achievementService;
    }

    @GetMapping("/tasks")
    public String tasks(Model model, HttpServletRequest request,
                        @RequestParam(value = "tag", required = false) String selectedTag,
                        Authentication authentication) {

        // Get all tasks and tags
        List<Task> tasks = taskService.getAllTasks();
        List<String> tags = taskService.getAllTags();

        // Filter tasks if tag is selected
        if (selectedTag != null && !selectedTag.equals("all")) {
            tasks = tasks.stream()
                    .filter(task -> task.getTags().contains(selectedTag))
                    .collect(Collectors.toList());
        }

        // Check if user is authenticated
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());

        model.addAttribute("tasks", tasks);
        model.addAttribute("tags", tags);
        model.addAttribute("selectedTag", selectedTag);
        model.addAttribute("isAuthenticated", isAuthenticated);

        if (isAuthenticated) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);

            model.addAttribute("username", username);
            model.addAttribute("role", user.getRole());

            // Get user stats nur wenn sie existieren
            UserStats userStats = gamificationService.getUserStats(user);
            model.addAttribute("userStats", userStats);

            // Get user's completed tasks
            Set<Long> completedTaskIds = userService.getCompletedTaskIds(user.getId());
            model.addAttribute("completedTaskIds", completedTaskIds);

            // Get user achievements nur wenn sie existieren
            List<java.util.Map<String, Object>> achievements = achievementService.getUserAchievements(user.getId());
            model.addAttribute("achievements", achievements);

            // Get learning path nur wenn es existiert
            LearningPath learningPath = gamificationService.getLearningPath(user);
            model.addAttribute("learningPath", learningPath);
        }

        return "tasks";
    }
}