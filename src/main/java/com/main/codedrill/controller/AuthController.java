package com.main.codedrill.controller;

import com.main.codedrill.model.User;
import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return "redirect:/";
        }
        return "login";
    }
    
    @GetMapping("/register")
    public String registerForm(Model model) {
        boolean registrationClosed = false;
        model.addAttribute("registrationClosed", registrationClosed);
        return "register";
    }
    
    @PostMapping("/register")
    public String register(@RequestParam String username, 
                           @RequestParam String password, 
                           @RequestParam String confirmPassword,
                           @RequestParam String email,
                           @RequestParam(required = false) String fullName,
                           Model model, 
                           RedirectAttributes redirectAttributes) {

        
        // Validate form
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "register";
        }
        
        if (password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters");
            return "register";
        }
        
        // Basic email validation
        if (!email.contains("@") || !email.contains(".")) {
            model.addAttribute("error", "Please enter a valid email address");
            return "register";
        }

        // Attempt to create user
        User user = userService.registerUser(username, password, fullName, email);

        if (user == null) {
            model.addAttribute("error", "Username or email already exists");
            return "register";
        }
        
        // Registration successful
        redirectAttributes.addFlashAttribute("registrationSuccess", true);
        redirectAttributes.addFlashAttribute("verificationEmailSent", true);
        redirectAttributes.addFlashAttribute("email", email);
        return "redirect:/login";
    }
    
    @GetMapping("/change-password")
    public String changePasswordForm(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }
        
        User user = userService.findByUsername(auth.getName());
        model.addAttribute("usingTempPassword", user != null && user.isUsingTempPassword());
        
        return "change-password";
    }
    
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }
        
        User user = userService.findByUsername(auth.getName());
        if (user == null) {
            return "redirect:/login";
        }
        
        // Check if passwords match
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match");
            return "redirect:/change-password";
        }
        
        // Validate new password
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters");
            return "redirect:/change-password";
        }
        
        // First check if user is using a temporary password
        if (user.isUsingTempPassword()) {
            // Skip current password validation for temporary passwords
            if (userService.updatePassword(user, newPassword)) {
                redirectAttributes.addFlashAttribute("success", "Password updated successfully");
                return "redirect:/";
            } else {
                redirectAttributes.addFlashAttribute("error", "Failed to update password");
                return "redirect:/change-password";
            }
        }
        
        // For regular password changes, validate current password
        if (!userService.validatePassword(user, currentPassword)) {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect");
            return "redirect:/change-password";
        }
        
        // Update password
        if (userService.updatePassword(user, newPassword)) {
            redirectAttributes.addFlashAttribute("success", "Password updated successfully");
            return "redirect:/";
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to update password");
            return "redirect:/change-password";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());
        
        if (user != null) {
            model.addAttribute("user", user);
            
            if (user.isAdmin()) {
                return "redirect:/admin";
            } else if (user.isModerator()) {
                return "redirect:/moderator";
            }
        }
        
        return "redirect:/";
    }
    
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
}

