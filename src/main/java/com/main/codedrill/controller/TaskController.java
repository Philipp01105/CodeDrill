package com.main.codedrill.controller;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.service.TaskCompletionService;
import com.main.codedrill.service.TaskService;
import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;
    private final TaskCompletionService taskCompletionService;

    @Autowired
    public TaskController(TaskService taskService, UserService userService, TaskCompletionService taskCompletionService) {
        this.taskService = taskService;
        this.userService = userService;
        this.taskCompletionService = taskCompletionService;
    }

    @GetMapping("/tasks")
    public String tasks(@RequestParam(required = false) String tag, Model model) {
        List<Task> tasks;
        if (tag != null && !tag.isEmpty()) {
            tasks = taskService.getTasksByTag(tag);
            model.addAttribute("selectedTag", tag);
        } else {
            tasks = taskService.getAllTasks();
            model.addAttribute("selectedTag", "all");
        }

        List<String> tags = taskService.getAllTags();
                
        model.addAttribute("tasks", tasks);
        model.addAttribute("tags", tags);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser");
        model.addAttribute("isAuthenticated", isAuthenticated);

        if (isAuthenticated) {
            User currentUser = userService.findByUsername(auth.getName());
            if (currentUser != null) {
                model.addAttribute("role", currentUser.getRole());
                List<Long> completedTaskIds = taskCompletionService.getCompletedTaskIds(currentUser);
                model.addAttribute("completedTaskIds", completedTaskIds);
            }
        }
        
        return "tasks";
    }
    
    @GetMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
        Task task = taskService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }
    
    // API endpoint for tasks CRUD
    @PostMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<Task> createTaskApi(@RequestBody Task task) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());
        
        if (currentUser == null || (!currentUser.isAdmin() && !currentUser.isModerator())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        
        Task createdTask = taskService.createTask(task, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTask);
    }
    
    @PutMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Task> updateTaskApi(@PathVariable Long id, @RequestBody Task task) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        
        task.setId(id);
        Task updatedTask = taskService.updateTask(task, currentUser);
        
        if (updatedTask == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        
        return ResponseEntity.ok(updatedTask);
    }
    
    @DeleteMapping("/api/tasks/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteTaskApi(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        boolean deleted = taskService.deleteTask(id, currentUser);
        
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.noContent().build();
    }
} 