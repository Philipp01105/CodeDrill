package com.main.codedrill.controller;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.service.TaskService;
import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/moderator")
public class ModeratorController {

    private final TaskService taskService;
    private final UserService userService;

    @Autowired
    public ModeratorController(TaskService taskService, UserService userService) {
        this.taskService = taskService;
        this.userService = userService;
    }

    @GetMapping
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());
        
        if (currentUser == null) {
            return "redirect:/login";
        }
        
        List<Task> userTasks = taskService.getTasksByUser(currentUser);
        int userTaskCount = userTasks.size();
        
        List<Task> recentTasks = userTasks.stream()
                .limit(5)
                .toList();
        
        model.addAttribute("user", currentUser);
        model.addAttribute("userTaskCount", userTaskCount);
        model.addAttribute("recentTasks", recentTasks);
        
        return "moderator/dashboard";
    }

    @GetMapping("/tasks")
    public String moderatorTasks(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());

        List<Task> tasks;
        if (currentUser.isAdmin()) {
            tasks = taskService.getAllTasks();
        } else {
            tasks = taskService.getTasksByUser(currentUser);
        }

        model.addAttribute("tasks", tasks);
        model.addAttribute("allTags", taskService.getAllTags());
        return "moderator/tasks";
    }

    // Task creation form
    @GetMapping("/tasks/new")
    public String newTaskForm(Model model) {
        model.addAttribute("task", new Task());
        model.addAttribute("allTags", taskService.getAllTags());
        return "moderator/task-form";
    }

    // Task edit form
    @GetMapping("/tasks/edit/{id}")
    public String editTaskForm(@PathVariable Long id, Model model) {
        Task task = taskService.getTaskById(id);
        if (task == null) {
            return "redirect:/moderator/tasks";
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());

        if (currentUser.isAdmin() ||
                (task.getCreatedBy() != null && task.getCreatedBy().equals(currentUser))) {

            model.addAttribute("task", task);
            model.addAttribute("allTags", taskService.getAllTags());
            return "moderator/task-form";
        }

        return "redirect:/moderator/tasks";
    }

    // Save or update task
    @PostMapping("/tasks/save")
    public String saveTask(@ModelAttribute Task task) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());

        if (task.getId() == null) {
            taskService.createTask(task, currentUser);
        } else {
            taskService.updateTask(task, currentUser);
        }

        return "redirect:/moderator/tasks";
    }

    // Delete task
    @PostMapping("/tasks/delete/{id}")
    public String deleteTask(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());

        taskService.deleteTask(id, currentUser);
        return "redirect:/moderator/tasks";
    }
} 