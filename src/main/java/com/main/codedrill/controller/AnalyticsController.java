package com.main.codedrill.controller;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import com.main.codedrill.service.AnalyticsService;
import com.main.codedrill.service.TaskService;
import com.main.codedrill.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/analytics")
public class AnalyticsController {


    Logger logger = org.slf4j.LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final TaskService taskService;

    @Autowired
    public AnalyticsController(AnalyticsService analyticsService, UserService userService, TaskService taskService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
        this.taskService = taskService;
    }

    @GetMapping("/user")
    public String getUserAnalytics(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());

        if (user == null) {
            return "redirect:/login";
        }

        Map<String, Object> metrics = analyticsService.getUserPerformanceMetrics(user);
        model.addAttribute("metrics", metrics);
        model.addAttribute("user", user);

        return "analytics/user-dashboard";
    }

    @GetMapping("/admin")
    public String getAdminAnalytics(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());

        if (user == null || !user.isAdmin()) {
            return "redirect:/login";
        }

        Map<String, Object> metrics = analyticsService.getAdminDashboardMetrics();
        model.addAttribute("metrics", metrics);
        model.addAttribute("user", user);

        return "analytics/admin-dashboard";
    }

    @GetMapping("/task/{taskId}")
    public String getTaskAnalytics(@PathVariable Long taskId, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUsername(auth.getName());

        if (user == null || (!user.isAdmin() && !user.isModerator())) {
            return "redirect:/login";
        }

        Task task = taskService.getTaskById(taskId);
        if (task == null) {
            return "redirect:/admin";
        }
        Map<String, Object> analytics = analyticsService.getTaskAnalytics(task);

        model.addAttribute("task", task);
        model.addAttribute("analytics", analytics);
        model.addAttribute("user", user);

        return "analytics/task-analytics";
    }

    @PostMapping("/track/view/{taskId}")
    @ResponseBody
    public String trackTaskView(@PathVariable Long taskId, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "Not authenticated";
        }

        User user = userService.findByUsername(auth.getName());
        Task task = taskService.getTaskById(taskId);
        logger.info("Tracking task view for user: {}, task: {}", user != null ? user.getUsername() : "null", task != null ? task.getTitle() : "null");

        if (user != null && task != null) {
            HttpSession session = request.getSession(true);
            String sessionId = session.getId();

            analyticsService.trackTaskView(user, task, sessionId);
            return "Success";
        }

        return "Failed";
    }

    @PostMapping("/track/login")
    @ResponseBody
    public String trackLogin(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "Not authenticated";
        }

        User user = userService.findByUsername(auth.getName());

        if (user != null) {
            HttpSession session = request.getSession(true);
            String sessionId = session.getId();

            analyticsService.trackUserLogin(user, sessionId, request);
            return "Success";
        }

        return "Failed";
    }

    @PostMapping("/track/logout")
    @ResponseBody
    public String trackLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String sessionId = session.getId();
            analyticsService.trackUserLogout(sessionId);
            return "Success";
        }

        return "No session";
    }

    @PostMapping("/track/attempt/{taskId}")
    @ResponseBody
    public String trackTaskAttempt(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "Not authenticated";
        }

        User user = userService.findByUsername(auth.getName());
        Task task = taskService.getTaskById(taskId);

        if (user != null && task != null) {
            HttpSession session = request.getSession(true);
            String sessionId = session.getId();

            boolean successful = false;
            if (payload.containsKey("successful")) {
                Object successObj = payload.get("successful");
                if (successObj instanceof Boolean) {
                    successful = (Boolean) successObj;
                } else if (successObj instanceof String) {
                    successful = Boolean.parseBoolean((String) successObj);
                }
            }

            String errorMessage = payload.containsKey("errorMessage") ? (String) payload.get("errorMessage") : "";
            String codeSubmitted = payload.containsKey("code") ? (String) payload.get("code") : "";

            analyticsService.trackTaskAttempt(user, task, codeSubmitted, successful, sessionId, errorMessage);
            return "Success";
        }

        return "Failed";
    }
}
