package com.main.codedrill.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_analytics")
public class UserAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(name = "time_spent_seconds")
    private Long timeSpentSeconds;

    @Column(name = "tasks_viewed")
    private Integer tasksViewed;

    @Column(name = "tasks_attempted")
    private Integer tasksAttempted;

    @Column(name = "tasks_completed")
    private Integer tasksCompleted;

    @Column(name = "average_completion_time_seconds")
    private Long averageCompletionTimeSeconds;

    @Column(name = "browser_info")
    private String browserInfo;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "ip_address")
    private String ipAddress;

    // Constructors
    public UserAnalytics() {
        this.loginTime = LocalDateTime.now();
        this.tasksViewed = 0;
        this.tasksAttempted = 0;
        this.tasksCompleted = 0;
    }

    public UserAnalytics(User user, String sessionId, String browserInfo, String deviceType, String ipAddress) {
        this.user = user;
        this.sessionId = sessionId;
        this.loginTime = LocalDateTime.now();
        this.browserInfo = browserInfo;
        this.deviceType = deviceType;
        this.ipAddress = ipAddress;
        this.tasksViewed = 0;
        this.tasksAttempted = 0;
        this.tasksCompleted = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(LocalDateTime logoutTime) {
        this.logoutTime = logoutTime;
        if (this.loginTime != null && logoutTime != null) {
            this.timeSpentSeconds = java.time.Duration.between(this.loginTime, logoutTime).getSeconds();
        }
    }

    public Long getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Long timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public Integer getTasksViewed() {
        return tasksViewed;
    }

    public void setTasksViewed(Integer tasksViewed) {
        this.tasksViewed = tasksViewed;
    }

    public void incrementTasksViewed() {
        if (this.tasksViewed == null) {
            this.tasksViewed = 1;
        } else {
            this.tasksViewed++;
        }
    }

    public Integer getTasksAttempted() {
        return tasksAttempted;
    }

    public void setTasksAttempted(Integer tasksAttempted) {
        this.tasksAttempted = tasksAttempted;
    }

    public void incrementTasksAttempted() {
        if (this.tasksAttempted == null) {
            this.tasksAttempted = 1;
        } else {
            this.tasksAttempted++;
        }
    }

    public Integer getTasksCompleted() {
        return tasksCompleted;
    }

    public void setTasksCompleted(Integer tasksCompleted) {
        this.tasksCompleted = tasksCompleted;
    }

    public void incrementTasksCompleted() {
        if (this.tasksCompleted == null) {
            this.tasksCompleted = 1;
        } else {
            this.tasksCompleted++;
        }
    }

    public Long getAverageCompletionTimeSeconds() {
        return averageCompletionTimeSeconds;
    }

    public void setAverageCompletionTimeSeconds(Long averageCompletionTimeSeconds) {
        this.averageCompletionTimeSeconds = averageCompletionTimeSeconds;
    }

    public String getBrowserInfo() {
        return browserInfo;
    }

    public void setBrowserInfo(String browserInfo) {
        this.browserInfo = browserInfo;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}