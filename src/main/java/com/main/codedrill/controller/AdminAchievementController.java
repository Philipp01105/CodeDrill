package com.main.codedrill.controller;

import com.main.codedrill.model.Achievement;
import com.main.codedrill.model.User;
import com.main.codedrill.service.AchievementService;
import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/achievements")
public class AdminAchievementController {

    private final AchievementService achievementService;
    private final UserService userService;

    @Autowired
    public AdminAchievementController(AchievementService achievementService, UserService userService) {
        this.achievementService = achievementService;
        this.userService = userService;
    }

    @GetMapping
    public String listAchievements(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        List<Achievement> achievements = achievementService.getAllAchievements();
        model.addAttribute("achievements", achievements);
        model.addAttribute("user", user);

        return "admin/achievements";
    }

    @GetMapping("/create")
    public String createAchievementForm(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        model.addAttribute("achievement", new Achievement());
        model.addAttribute("user", user);

        return "admin/create-achievement";
    }

    @PostMapping("/create")
    public String createAchievement(@ModelAttribute Achievement achievement,
                                    RedirectAttributes redirectAttributes,
                                    Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        try {
            achievementService.createAchievement(achievement);
            redirectAttributes.addFlashAttribute("success", "Achievement created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating achievement: " + e.getMessage());
        }

        return "redirect:/admin/achievements";
    }

    @GetMapping("/edit/{id}")
    public String editAchievementForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        Achievement achievement = achievementService.getAchievementById(id);
        if (achievement == null) {
            return "redirect:/admin/achievements";
        }

        model.addAttribute("achievement", achievement);
        model.addAttribute("user", user);

        return "admin/edit-achievement";
    }

    @PostMapping("/edit/{id}")
    public String updateAchievement(@PathVariable Long id,
                                    @ModelAttribute Achievement achievement,
                                    RedirectAttributes redirectAttributes,
                                    Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        try {
            achievement.setId(id);
            achievementService.updateAchievement(achievement);
            redirectAttributes.addFlashAttribute("success", "Achievement updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating achievement: " + e.getMessage());
        }

        return "redirect:/admin/achievements";
    }

    @PostMapping("/delete/{id}")
    public String deleteAchievement(@PathVariable Long id,
                                    RedirectAttributes redirectAttributes,
                                    Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        try {
            achievementService.deleteAchievement(id);
            redirectAttributes.addFlashAttribute("success", "Achievement deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting achievement: " + e.getMessage());
        }

        return "redirect:/admin/achievements";
    }

    @PostMapping("/toggle/{id}")
    public String toggleAchievement(@PathVariable Long id,
                                    RedirectAttributes redirectAttributes,
                                    Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        if (!user.isAdmin()) {
            return "redirect:/";
        }

        try {
            achievementService.toggleAchievementStatus(id);
            redirectAttributes.addFlashAttribute("success", "Achievement status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating achievement status: " + e.getMessage());
        }

        return "redirect:/admin/achievements";
    }
}