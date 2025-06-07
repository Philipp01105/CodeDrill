package com.main.codedrill.controller;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.service.TaskService;
import com.main.codedrill.service.UserService;
import com.main.codedrill.service.GamificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/learning-paths")
public class AdminLearningPathController {

    private final UserService userService;
    private final TaskService taskService;
    private final GamificationService gamificationService;

    @Autowired
    public AdminLearningPathController(UserService userService,
                                       TaskService taskService,
                                       GamificationService gamificationService) {
        this.userService = userService;
        this.taskService = taskService;
        this.gamificationService = gamificationService;
    }

    @GetMapping
    public String manageLearningPaths(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        List<Task> tasks = taskService.getAllTasks();
        Map<String, List<Task>> tasksByCategory = taskService.getTasksByCategory();

        model.addAttribute("tasks", tasks);
        model.addAttribute("tasksByCategory", tasksByCategory);
        model.addAttribute("user", user);

        return "admin/learning-paths";
    }

    @PostMapping("/update-task-category")
    public String updateTaskCategory(@RequestParam Long taskId,
                                     @RequestParam String category,
                                     @RequestParam(required = false) Integer xpReward,
                                     @RequestParam(required = false) String difficulty,
                                     @RequestParam(required = false) String estimatedTime,
                                     RedirectAttributes redirectAttributes,
                                     Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        try {
            Task task = taskService.getTaskById(taskId);
            if (task != null) {
                task.setLearningCategory(category);
                if (xpReward != null) {
                    task.setXpReward(xpReward);
                }
                if (difficulty != null && !difficulty.isEmpty()) {
                    task.setDifficulty(Task.TaskDifficulty.valueOf(difficulty.toUpperCase()));
                }
                if (estimatedTime != null && !estimatedTime.isEmpty()) {
                    task.setEstimatedTime(estimatedTime);
                }
                taskService.updateTask(task);
                redirectAttributes.addFlashAttribute("success", "Task updated successfully!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating task: " + e.getMessage());
        }

        return "redirect:/admin/learning-paths";
    }

    @PostMapping("/bulk-update")
    public String bulkUpdateTasks(@RequestParam List<Long> taskIds,
                                  @RequestParam String action,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) Integer xpReward,
                                  @RequestParam(required = false) String difficulty,
                                  RedirectAttributes redirectAttributes,
                                  Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        try {
            int updatedCount = 0;
            for (Long taskId : taskIds) {
                Task task = taskService.getTaskById(taskId);
                if (task != null) {
                    switch (action) {
                        case "update-category":
                            if (category != null && !category.isEmpty()) {
                                task.setLearningCategory(category);
                            }
                            break;
                        case "update-xp":
                            if (xpReward != null) {
                                task.setXpReward(xpReward);
                            }
                            break;
                        case "update-difficulty":
                            if (difficulty != null && !difficulty.isEmpty()) {
                                task.setDifficulty(Task.TaskDifficulty.valueOf(difficulty.toUpperCase()));
                            }
                            break;
                    }
                    taskService.updateTask(task);
                    updatedCount++;
                }
            }
            redirectAttributes.addFlashAttribute("success", updatedCount + " tasks updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating tasks: " + e.getMessage());
        }

        return "redirect:/admin/learning-paths";
    }
}