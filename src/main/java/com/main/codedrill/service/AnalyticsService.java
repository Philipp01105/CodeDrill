package com.main.codedrill.service;

import com.main.codedrill.model.*;
import com.main.codedrill.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnalyticsService {

    private final UserAnalyticsRepository userAnalyticsRepository;
    private final TaskAttemptRepository taskAttemptRepository;
    private final SystemAnalyticsRepository systemAnalyticsRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @Autowired
    public AnalyticsService(
            UserAnalyticsRepository userAnalyticsRepository,
            TaskAttemptRepository taskAttemptRepository,
            SystemAnalyticsRepository systemAnalyticsRepository,
            UserRepository userRepository,
            TaskRepository taskRepository) {
        this.userAnalyticsRepository = userAnalyticsRepository;
        this.taskAttemptRepository = taskAttemptRepository;
        this.systemAnalyticsRepository = systemAnalyticsRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void trackUserLogin(User user, String sessionId, HttpServletRequest request) {
        String browserInfo = extractBrowserInfo(request);
        String deviceType = extractDeviceType(request);
        String ipAddress = extractIpAddress(request);

        UserAnalytics userAnalyticsT = new UserAnalytics(user, sessionId, browserInfo, deviceType, ipAddress);
        userAnalyticsRepository.save(userAnalyticsT);

        updateSystemAnalyticsForLogin(user, browserInfo, deviceType);

    }

    @Transactional
    public void trackUserLogout(String sessionId) {
        Optional<UserAnalytics> analyticsOpt = userAnalyticsRepository.findBySessionId(sessionId);
        if (analyticsOpt.isPresent()) {
            UserAnalytics analytics = analyticsOpt.get();
            analytics.setLogoutTime(LocalDateTime.now());
            userAnalyticsRepository.save(analytics);

            updateSystemAnalyticsForLogout(analytics);
        }
    }

    @Transactional
    public void trackTaskView(User user, Task task, String sessionId) {
        Optional<UserAnalytics> analyticsOpt = userAnalyticsRepository.findBySessionId(sessionId);
        if (analyticsOpt.isPresent()) {
            UserAnalytics analytics = analyticsOpt.get();
            analytics.incrementTasksViewed();
            userAnalyticsRepository.save(analytics);
        }

        updateSystemAnalyticsForTaskView();
    }

    @Transactional
    public void trackTaskAttempt(User user, Task task, String codeSubmitted, boolean successful, String sessionId, String errorMessage) {
        TaskAttempt attempt = new TaskAttempt(user, task, codeSubmitted, successful, sessionId);
        attempt.setErrorMessage(errorMessage);
        taskAttemptRepository.save(attempt);

        Optional<UserAnalytics> analyticsOpt = userAnalyticsRepository.findBySessionId(sessionId);
        if (analyticsOpt.isPresent()) {
            UserAnalytics analytics = analyticsOpt.get();
            analytics.incrementTasksAttempted();

            if (successful) {long previousSuccessfulAttempts = taskAttemptRepository.findByUserAndTaskAndSuccessful(user, task, true)
                        .stream()
                        .filter(att -> !att.getId().equals(attempt.getId()))
                        .count();

                if (previousSuccessfulAttempts == 0) {
                    analytics.incrementTasksCompleted();
                }
            }

            userAnalyticsRepository.save(analytics);
        }

        updateSystemAnalyticsForTaskAttempt(successful);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserPerformanceMetrics(User user) {
        Map<String, Object> metrics = new HashMap<>();

        List<UserAnalytics> userAnalyticsList = userAnalyticsRepository.findByUserOrderByLoginTimeDesc(user);

        long totalTimeSpent = userAnalyticsList.stream()
                .filter(ua -> ua.getTimeSpentSeconds() != null)
                .mapToLong(UserAnalytics::getTimeSpentSeconds)
                .sum();

        int totalTasksViewed = userAnalyticsList.stream()
                .filter(ua -> ua.getTasksViewed() != null)
                .mapToInt(UserAnalytics::getTasksViewed)
                .sum();

        int totalTasksAttempted = userAnalyticsList.stream()
                .filter(ua -> ua.getTasksAttempted() != null)
                .mapToInt(UserAnalytics::getTasksAttempted)
                .sum();

        int totalTasksCompleted = userAnalyticsList.stream()
                .filter(ua -> ua.getTasksCompleted() != null)
                .mapToInt(UserAnalytics::getTasksCompleted)
                .sum();

        double successRate = totalTasksAttempted > 0 ? (double) totalTasksCompleted / totalTasksAttempted : 0;

        List<Object[]> mostAttemptedTasks = taskAttemptRepository.findMostAttemptedTasksByUser(user);
        List<Map<String, Object>> topTasks = new ArrayList<>();

        for (Object[] result : mostAttemptedTasks.subList(0, Math.min(5, mostAttemptedTasks.size()))) {
            Long taskId = (Long) result[0];
            Long attemptCount = (Long) result[1];

            Optional<Task> taskOpt = taskRepository.findById(taskId);
            if (taskOpt.isPresent()) {
                Map<String, Object> taskMap = new HashMap<>();
                taskMap.put("task", taskOpt.get());
                taskMap.put("attemptCount", attemptCount);
                topTasks.add(taskMap);
            }
        }

        Map<String, Object> activityData = getUserActivityOverTime(user, 7);

        metrics.put("totalTimeSpent", totalTimeSpent);
        metrics.put("totalTasksViewed", totalTasksViewed);
        metrics.put("totalTasksAttempted", totalTasksAttempted);
        metrics.put("totalTasksCompleted", totalTasksCompleted);
        metrics.put("successRate", successRate);
        metrics.put("topAttemptedTasks", topTasks);
        metrics.put("sessionCount", userAnalyticsList.size());
        metrics.put("activityData", activityData);

        return metrics;
    }

    /**
     * Get user activity data over a specified number of days
     *
     * @param user The user to get activity for
     * @param days Number of days to include
     * @return Map containing activity data
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserActivityOverTime(User user, int days) {
        Map<String, Object> activityData = new HashMap<>();

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        List<TaskAttempt> attempts = taskAttemptRepository.findByUserAndAttemptTimeBetween(user, startDate, endDate);

        List<String> labels = new ArrayList<>();
        List<Integer> viewedData = new ArrayList<>();
        List<Integer> attemptedData = new ArrayList<>();
        List<Integer> completedData = new ArrayList<>();

        Map<LocalDate, Integer> viewedByDay = new HashMap<>();
        Map<LocalDate, Integer> attemptedByDay = new HashMap<>();
        Map<LocalDate, Integer> completedByDay = new HashMap<>();

        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            viewedByDay.put(date, 0);
            attemptedByDay.put(date, 0);
            completedByDay.put(date, 0);
        }

        for (TaskAttempt attempt : attempts) {
            LocalDate attemptDate = attempt.getAttemptTime().toLocalDate();

            if (attemptDate.isAfter(startDate.toLocalDate().minusDays(1)) &&
                    attemptDate.isBefore(endDate.toLocalDate().plusDays(1))) {

                attemptedByDay.put(attemptDate, attemptedByDay.getOrDefault(attemptDate, 0) + 1);

                if (attempt.getSuccessful() != null && attempt.getSuccessful()) {
                    completedByDay.put(attemptDate, completedByDay.getOrDefault(attemptDate, 0) + 1);
                }
            }
        }

        List<UserAnalytics> userAnalytics = userAnalyticsRepository.findByLoginTimeBetween(startDate, endDate);
        userAnalytics = userAnalytics.stream()
                .filter(ua -> ua.getUser().getId().equals(user.getId()))
                .toList();

        for (UserAnalytics ua : userAnalytics) {
            LocalDate loginDate = ua.getLoginTime().toLocalDate();

            if (loginDate.isAfter(startDate.toLocalDate().minusDays(1)) &&
                    loginDate.isBefore(endDate.toLocalDate().plusDays(1))) {

                int tasksViewed = ua.getTasksViewed() != null ? ua.getTasksViewed() : 0;
                viewedByDay.put(loginDate, viewedByDay.getOrDefault(loginDate, 0) + tasksViewed);
            }
        }

        List<LocalDate> sortedDates = new ArrayList<>(viewedByDay.keySet());
        Collections.sort(sortedDates);

        for (LocalDate date : sortedDates) {
            String formattedDate = date.getMonth().toString().substring(0, 3) + " " + date.getDayOfMonth();
            labels.add(formattedDate);

            viewedData.add(viewedByDay.get(date));
            attemptedData.add(attemptedByDay.get(date));
            completedData.add(completedByDay.get(date));
        }

        activityData.put("labels", labels);
        activityData.put("viewedData", viewedData);
        activityData.put("attemptedData", attemptedData);
        activityData.put("completedData", completedData);

        return activityData;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminDashboardMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDate sevenDaysAgo = today.minusDays(7);

        Optional<SystemAnalytics> todayAnalyticsOpt = systemAnalyticsRepository.findByDate(today);
        List<SystemAnalytics> last7DaysAnalytics = systemAnalyticsRepository.findByDateBetween(sevenDaysAgo, today);
        List<SystemAnalytics> last30DaysAnalytics = systemAnalyticsRepository.findByDateBetween(thirtyDaysAgo, today);

        SystemAnalytics todayAnalytics = todayAnalyticsOpt.orElse(new SystemAnalytics());
        metrics.put("todayActiveUsers", todayAnalytics.getActiveUsers());
        metrics.put("todayNewUsers", todayAnalytics.getNewUsers());
        metrics.put("todayTaskViews", todayAnalytics.getTotalTaskViews());
        metrics.put("todayTaskAttempts", todayAnalytics.getTotalTaskAttempts());
        metrics.put("todayTaskCompletions", todayAnalytics.getTotalTaskCompletions());

        metrics.put("last7DaysActiveUsers", systemAnalyticsRepository.sumActiveUsersBetween(sevenDaysAgo, today));
        metrics.put("last7DaysNewUsers", systemAnalyticsRepository.sumNewUsersBetween(sevenDaysAgo, today));
        metrics.put("last7DaysTaskViews", systemAnalyticsRepository.sumTaskViewsBetween(sevenDaysAgo, today));
        metrics.put("last7DaysTaskAttempts", systemAnalyticsRepository.sumTaskAttemptsBetween(sevenDaysAgo, today));
        metrics.put("last7DaysTaskCompletions", systemAnalyticsRepository.sumTaskCompletionsBetween(sevenDaysAgo, today));
        metrics.put("last7DaysAvgSessionTime", systemAnalyticsRepository.avgSessionTimeBetween(sevenDaysAgo, today));
        metrics.put("last7DaysSuccessRate", systemAnalyticsRepository.avgSuccessRateBetween(sevenDaysAgo, today));

        metrics.put("last30DaysActiveUsers", systemAnalyticsRepository.sumActiveUsersBetween(thirtyDaysAgo, today));
        metrics.put("last30DaysNewUsers", systemAnalyticsRepository.sumNewUsersBetween(thirtyDaysAgo, today));
        metrics.put("last30DaysTaskViews", systemAnalyticsRepository.sumTaskViewsBetween(thirtyDaysAgo, today));
        metrics.put("last30DaysTaskAttempts", systemAnalyticsRepository.sumTaskAttemptsBetween(thirtyDaysAgo, today));
        metrics.put("last30DaysTaskCompletions", systemAnalyticsRepository.sumTaskCompletionsBetween(thirtyDaysAgo, today));
        metrics.put("last30DaysAvgSessionTime", systemAnalyticsRepository.avgSessionTimeBetween(thirtyDaysAgo, today));
        metrics.put("last30DaysSuccessRate", systemAnalyticsRepository.avgSuccessRateBetween(thirtyDaysAgo, today));

        metrics.put("chromeUsers", systemAnalyticsRepository.sumChromeUsersBetween(thirtyDaysAgo, today));
        metrics.put("firefoxUsers", systemAnalyticsRepository.sumFirefoxUsersBetween(thirtyDaysAgo, today));
        metrics.put("safariUsers", systemAnalyticsRepository.sumSafariUsersBetween(thirtyDaysAgo, today));
        metrics.put("edgeUsers", systemAnalyticsRepository.sumEdgeUsersBetween(thirtyDaysAgo, today));
        metrics.put("otherBrowsers", systemAnalyticsRepository.sumOtherBrowsersBetween(thirtyDaysAgo, today));

        metrics.put("desktopUsers", systemAnalyticsRepository.sumDesktopUsersBetween(thirtyDaysAgo, today));
        metrics.put("mobileUsers", systemAnalyticsRepository.sumMobileUsersBetween(thirtyDaysAgo, today));
        metrics.put("tabletUsers", systemAnalyticsRepository.sumTabletUsersBetween(thirtyDaysAgo, today));

        metrics.put("activeUsersTrend", systemAnalyticsRepository.findActiveUsersTrendBetween(thirtyDaysAgo, today));
        metrics.put("taskCompletionsTrend", systemAnalyticsRepository.findTaskCompletionsTrendBetween(thirtyDaysAgo, today));

        return metrics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTaskAnalytics(Task task) {
        Map<String, Object> analytics = new HashMap<>();

        List<TaskAttempt> attempts = taskAttemptRepository.findByTask(task);

        long totalAttempts = attempts.size();
        long successfulAttempts = attempts.stream()
                .filter(ta -> ta.getSuccessful() != null && ta.getSuccessful())
                .count();

        double successRate = totalAttempts > 0 ? (double) successfulAttempts / totalAttempts : 0;

        Double averageTimeSpent = taskAttemptRepository.averageTimeSpentByTask(task);

        List<Object[]> userAttempts = taskAttemptRepository.findUsersByMostAttemptsOnTask(task);
        List<Map<String, Object>> topUsers = new ArrayList<>();

        for (Object[] result : userAttempts.subList(0, Math.min(5, userAttempts.size()))) {
            Long userId = (Long) result[0];
            Long attemptCount = (Long) result[1];

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("user", userOpt.get());
                userMap.put("attemptCount", attemptCount);
                topUsers.add(userMap);
            }
        }

        analytics.put("totalAttempts", totalAttempts);
        analytics.put("successfulAttempts", successfulAttempts);
        analytics.put("successRate", successRate);
        analytics.put("averageTimeSpent", averageTimeSpent);
        analytics.put("topUsers", topUsers);

        return analytics;
    }

    // Helper methods for extracting client information
    private String extractBrowserInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return "Unknown";
        }

        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("chrome") && !userAgent.contains("edge")) {
            return "Chrome";
        } else if (userAgent.contains("firefox")) {
            return "Firefox";
        } else if (userAgent.contains("safari") && !userAgent.contains("chrome")) {
            return "Safari";
        } else if (userAgent.contains("edge")) {
            return "Edge";
        } else {
            return "Other";
        }
    }

    private String extractDeviceType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return "Unknown";
        }

        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("mobile")) {
            return "Mobile";
        } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
            return "Tablet";
        } else {
            return "Desktop";
        }
    }

    private String extractIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    // Helper methods for updating system analytics
    @Transactional
    protected void updateSystemAnalyticsForLogin(User user, String browserInfo, String deviceType) {
        LocalDate today = LocalDate.now();
        SystemAnalytics systemAnalytics = systemAnalyticsRepository.findByDate(today)
                .orElse(new SystemAnalytics());

        if (isFirstLoginIn24Hours(user)) {
            systemAnalytics.incrementActiveUsers();
            System.out.println("Active user incremented for: " + user.getUsername());
        }else {
            System.out.println("Active user not incremented for: " + user.getUsername());
        }
        systemAnalytics.incrementTotalSessions();

        if ("Chrome".equals(browserInfo)) {
            systemAnalytics.incrementChromeUsers();
        } else if ("Firefox".equals(browserInfo)) {
            systemAnalytics.incrementFirefoxUsers();
        } else if ("Safari".equals(browserInfo)) {
            systemAnalytics.incrementSafariUsers();
        } else if ("Edge".equals(browserInfo)) {
            systemAnalytics.incrementEdgeUsers();
        } else {
            systemAnalytics.incrementOtherBrowsers();
        }

        if ("Desktop".equals(deviceType)) {
            systemAnalytics.incrementDesktopUsers();
        } else if ("Mobile".equals(deviceType)) {
            systemAnalytics.incrementMobileUsers();
        } else if ("Tablet".equals(deviceType)) {
            systemAnalytics.incrementTabletUsers();
        }

        systemAnalytics.updateLastUpdated();
        systemAnalyticsRepository.save(systemAnalytics);
    }

    /**
     * Prüft, ob der Benutzer in den letzten 24 Stunden bereits aktiv war
     * @param user Der zu prüfende Benutzer
     * @return true, wenn es der erste Login innerhalb 24 Stunden ist
     */
    private boolean isFirstLoginIn24Hours(User user) {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<UserAnalytics> recentActivity = userAnalyticsRepository.findByUserAndLoginTimeAfter(user, twentyFourHoursAgo);
        return recentActivity.size() <= 1;
    }

    @Transactional
    protected void updateSystemAnalyticsForLogout(UserAnalytics userAnalytics) {
        if (userAnalytics.getTimeSpentSeconds() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        SystemAnalytics systemAnalytics = systemAnalyticsRepository.findByDate(today)
                .orElse(new SystemAnalytics());

        Long currentAvg = systemAnalytics.getAverageSessionTimeSeconds();
        Integer totalSessions = systemAnalytics.getTotalSessions();

        if (currentAvg == null || totalSessions == null || totalSessions <= 1) {
            systemAnalytics.setAverageSessionTimeSeconds(userAnalytics.getTimeSpentSeconds());
        } else {
            Long newAvg = ((currentAvg * (totalSessions - 1)) + userAnalytics.getTimeSpentSeconds()) / totalSessions;
            systemAnalytics.setAverageSessionTimeSeconds(newAvg);
        }

        systemAnalytics.updateLastUpdated();
        systemAnalyticsRepository.save(systemAnalytics);
    }

    @Transactional
    protected void updateSystemAnalyticsForTaskView() {
        LocalDate today = LocalDate.now();
        SystemAnalytics systemAnalytics = systemAnalyticsRepository.findByDate(today)
                .orElse(new SystemAnalytics());

        systemAnalytics.incrementTotalTaskViews();
        systemAnalytics.updateLastUpdated();
        systemAnalyticsRepository.save(systemAnalytics);
    }

    @Transactional
    protected void updateSystemAnalyticsForTaskAttempt(boolean successful) {
        LocalDate today = LocalDate.now();
        SystemAnalytics systemAnalytics = systemAnalyticsRepository.findByDate(today)
                .orElse(new SystemAnalytics());

        systemAnalytics.incrementTotalTaskAttempts();
        if (successful) {
            systemAnalytics.incrementTotalTaskCompletions();
        }

        systemAnalytics.calculateSuccessRate();
        systemAnalytics.updateLastUpdated();
        systemAnalyticsRepository.save(systemAnalytics);
    }

    @Transactional
    protected void trackNewUserRegistration() {
        LocalDate today = LocalDate.now();
        SystemAnalytics systemAnalytics = systemAnalyticsRepository.findByDate(today)
                .orElse(new SystemAnalytics());

        systemAnalytics.incrementNewUsers();
        systemAnalytics.updateLastUpdated();
        systemAnalyticsRepository.save(systemAnalytics);
    }
}

