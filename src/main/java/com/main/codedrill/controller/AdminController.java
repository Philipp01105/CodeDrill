package com.main.codedrill.controller;

import com.main.codedrill.model.User;
import com.main.codedrill.repository.TaskRepository;
import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final ApplicationContext applicationContext;
    private final TaskRepository taskRepository;

    @Autowired
    public AdminController(UserService userService, ApplicationContext applicationContext, TaskRepository taskRepository) {
        this.userService = userService;
        this.applicationContext = applicationContext;
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public String adminDashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());
        
        int userCount = userService.countAllUsers();
        int moderatorCount = userService.countAllModerators();
        
        model.addAttribute("user", user);
        model.addAttribute("userCount", userCount);
        model.addAttribute("moderatorCount", moderatorCount);
        model.addAttribute("moderators", userService.findAllModerators());
        model.addAttribute("taskCount", taskRepository.count());
        
        return "admin/dashboard";
    }

    @GetMapping("/moderators")
    public String listModerators(Model model) {
        model.addAttribute("moderators", userService.findAllModerators());
        return "admin/moderators";
    }

    @GetMapping("/moderators/new")
    public String newModeratorForm(Model model) {
        model.addAttribute("moderator", new User());
        return "admin/moderator-form";
    }

    @PostMapping("/moderators/save")
    public String saveModerator(@ModelAttribute("moderator") User moderator, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());

        User savedModerator = userService.createModerator(moderator, admin);
        
        if (savedModerator == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Username already exists or you do not have permission");
            return "redirect:/admin/moderators/new";
        }
        
        redirectAttributes.addFlashAttribute("successMessage", "Moderator created successfully");
        return "redirect:/admin/moderators";
    }

    @GetMapping("/moderators/reset-password/{id}")
    public String resetPasswordForm(@PathVariable Long id, Model model) {
        model.addAttribute("userId", id);
        return "admin/reset-password";
    }

    @PostMapping("/moderators/reset-password")
    public String resetPassword(@RequestParam Long userId, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());
        
        String tempPassword = userService.resetPasswordWithTemp(userId, admin);
        
        if (tempPassword == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to reset password");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Password reset successfully");
            redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
            redirectAttributes.addFlashAttribute("userId", userId);
        }
        
        return "redirect:/admin/moderators";
    }

    @PostMapping("/moderators/delete/{id}")
    public String deleteModerator(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());

        boolean deleted = userService.deleteUser(id, admin);
        
        if (!deleted) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete moderator");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Moderator deleted successfully");
        }
        
        return "redirect:/admin/moderators";
    }

    @GetMapping("/users")
    public String listUsers(
            @RequestParam(required = false) String search,
            Model model) {
        
        List<User> users;
        if (search != null && !search.trim().isEmpty()) {
            users = userService.searchUsersByUsername(search);
            model.addAttribute("search", search);
        } else {
            users = userService.findAllUsers();
        }
        
        model.addAttribute("users", users);
        return "admin/users";
    }
    
    @PostMapping("/users/{userId}/reset-password")
    public String resetUserPassword(
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());
        
        if (admin != null && admin.isAdmin()) {
            String tempPassword = userService.resetPasswordWithTemp(userId, admin);
            
            if (tempPassword != null) {
                redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
                redirectAttributes.addFlashAttribute("userId", userId);
                return "redirect:/admin/users";
            }
        }
        
        redirectAttributes.addFlashAttribute("error", "Failed to reset password");
        return "redirect:/admin/users";
    }
    
    @PostMapping("/users/{userId}/delete")
    public String deleteUser(
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());
        
        if (admin != null && admin.isAdmin()) {
            if (userService.deleteUser(userId, admin)) {
                redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete user");
            }
        }
        
        return "redirect:/admin/users";
    }

    @PostMapping("/users/promote/{userid}")
    public String promoteUserToModerator(@PathVariable Long userid, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());

        if (admin == null || !admin.isAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access attempt detected");
            return "redirect:/admin/users";
        }

        User user = userService.findById(userid);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found");
            return "redirect:/admin/users";
        }

        User savedModerator = userService.setModerator(user, admin);

        if (savedModerator == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to promote user to moderator");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "User promoted to moderator successfully");
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/users/demote/{userid}")
    public String demoteModeratorToUser(@PathVariable Long userid, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());

        if (admin == null || !admin.isAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access attempt detected");
            return "redirect:/admin/users";
        }

        User user = userService.findById(userid);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found");
            return "redirect:/admin/users";
        }

        User savedUser = userService.setUser(user, admin);

        if (savedUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to demote moderator to user");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Moderator demoted to user successfully");
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/emergency-shutdown")
    public String emergencyShutdown(@RequestParam String password, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userService.findByUsername(auth.getName());
        
        if (admin == null || !admin.isAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access attempt detected");
            return "redirect:/admin";
        }

        if (userService.validatePassword(admin, password)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid password. Emergency shutdown aborted.");
            return "redirect:/admin";
        }

        System.out.println("EMERGENCY SHUTDOWN initiated by admin: " + admin.getUsername() + " at " + java.time.LocalDateTime.now());

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                SpringApplication.exit(applicationContext, () -> 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        redirectAttributes.addFlashAttribute("successMessage", "System shutdown initiated");
        return "redirect:/admin";
    }
} 