package com.main.codedrill.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_analytics")
public class SystemAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;
    
    @Column(name = "active_users")
    private Integer activeUsers;
    
    @Column(name = "new_users")
    private Integer newUsers;
    
    @Column(name = "total_sessions")
    private Integer totalSessions;
    
    @Column(name = "average_session_time_seconds")
    private Long averageSessionTimeSeconds;
    
    @Column(name = "total_task_views")
    private Integer totalTaskViews;
    
    @Column(name = "total_task_attempts")
    private Integer totalTaskAttempts;
    
    @Column(name = "total_task_completions")
    private Integer totalTaskCompletions;
    
    @Column(name = "success_rate")
    private Double successRate;
    
    @Column(name = "chrome_users")
    private Integer chromeUsers;
    
    @Column(name = "firefox_users")
    private Integer firefoxUsers;
    
    @Column(name = "safari_users")
    private Integer safariUsers;
    
    @Column(name = "edge_users")
    private Integer edgeUsers;
    
    @Column(name = "other_browsers")
    private Integer otherBrowsers;
    
    @Column(name = "desktop_users")
    private Integer desktopUsers;
    
    @Column(name = "mobile_users")
    private Integer mobileUsers;
    
    @Column(name = "tablet_users")
    private Integer tabletUsers;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
    
    // Constructors
    public SystemAnalytics() {
        this.date = LocalDate.now();
        this.lastUpdated = LocalDateTime.now();
        this.activeUsers = 0;
        this.newUsers = 0;
        this.totalSessions = 0;
        this.totalTaskViews = 0;
        this.totalTaskAttempts = 0;
        this.totalTaskCompletions = 0;
        this.chromeUsers = 0;
        this.firefoxUsers = 0;
        this.safariUsers = 0;
        this.edgeUsers = 0;
        this.otherBrowsers = 0;
        this.desktopUsers = 0;
        this.mobileUsers = 0;
        this.tabletUsers = 0;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public void setDate(LocalDate date) {
        this.date = date;
    }
    
    public Integer getActiveUsers() {
        return activeUsers;
    }
    
    public void setActiveUsers(Integer activeUsers) {
        this.activeUsers = activeUsers;
    }
    
    public void incrementActiveUsers() {
        if (this.activeUsers == null) {
            this.activeUsers = 1;
        } else {
            this.activeUsers++;
        }
    }
    
    public void decrementActiveUsers() {
        if (this.activeUsers != null && this.activeUsers > 0) {
            this.activeUsers--;
        }
    }

    public Integer getNewUsers() {
        return newUsers;
    }
    
    public void setNewUsers(Integer newUsers) {
        this.newUsers = newUsers;
    }
    
    public void incrementNewUsers() {
        if (this.newUsers == null) {
            this.newUsers = 1;
        } else {
            this.newUsers++;
        }
    }
    
    public Integer getTotalSessions() {
        return totalSessions;
    }
    
    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }
    
    public void incrementTotalSessions() {
        if (this.totalSessions == null) {
            this.totalSessions = 1;
        } else {
            this.totalSessions++;
        }
    }
    
    public Long getAverageSessionTimeSeconds() {
        return averageSessionTimeSeconds;
    }
    
    public void setAverageSessionTimeSeconds(Long averageSessionTimeSeconds) {
        this.averageSessionTimeSeconds = averageSessionTimeSeconds;
    }
    
    public Integer getTotalTaskViews() {
        return totalTaskViews;
    }
    
    public void setTotalTaskViews(Integer totalTaskViews) {
        this.totalTaskViews = totalTaskViews;
    }
    
    public void incrementTotalTaskViews() {
        if (this.totalTaskViews == null) {
            this.totalTaskViews = 1;
        } else {
            this.totalTaskViews++;
        }
    }
    
    public Integer getTotalTaskAttempts() {
        return totalTaskAttempts;
    }
    
    public void setTotalTaskAttempts(Integer totalTaskAttempts) {
        this.totalTaskAttempts = totalTaskAttempts;
    }
    
    public void incrementTotalTaskAttempts() {
        if (this.totalTaskAttempts == null) {
            this.totalTaskAttempts = 1;
        } else {
            this.totalTaskAttempts++;
        }
    }
    
    public Integer getTotalTaskCompletions() {
        return totalTaskCompletions;
    }
    
    public void setTotalTaskCompletions(Integer totalTaskCompletions) {
        this.totalTaskCompletions = totalTaskCompletions;
    }
    
    public void incrementTotalTaskCompletions() {
        if (this.totalTaskCompletions == null) {
            this.totalTaskCompletions = 1;
        } else {
            this.totalTaskCompletions++;
        }
    }
    
    public Double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }
    
    public void calculateSuccessRate() {
        if (this.totalTaskAttempts != null && this.totalTaskAttempts > 0 && this.totalTaskCompletions != null) {
            this.successRate = (double) this.totalTaskCompletions / this.totalTaskAttempts;
        }
    }
    
    public Integer getChromeUsers() {
        return chromeUsers;
    }
    
    public void setChromeUsers(Integer chromeUsers) {
        this.chromeUsers = chromeUsers;
    }
    
    public void incrementChromeUsers() {
        if (this.chromeUsers == null) {
            this.chromeUsers = 1;
        } else {
            this.chromeUsers++;
        }
    }
    
    public Integer getFirefoxUsers() {
        return firefoxUsers;
    }
    
    public void setFirefoxUsers(Integer firefoxUsers) {
        this.firefoxUsers = firefoxUsers;
    }
    
    public void incrementFirefoxUsers() {
        if (this.firefoxUsers == null) {
            this.firefoxUsers = 1;
        } else {
            this.firefoxUsers++;
        }
    }
    
    public Integer getSafariUsers() {
        return safariUsers;
    }
    
    public void setSafariUsers(Integer safariUsers) {
        this.safariUsers = safariUsers;
    }
    
    public void incrementSafariUsers() {
        if (this.safariUsers == null) {
            this.safariUsers = 1;
        } else {
            this.safariUsers++;
        }
    }
    
    public Integer getEdgeUsers() {
        return edgeUsers;
    }
    
    public void setEdgeUsers(Integer edgeUsers) {
        this.edgeUsers = edgeUsers;
    }
    
    public void incrementEdgeUsers() {
        if (this.edgeUsers == null) {
            this.edgeUsers = 1;
        } else {
            this.edgeUsers++;
        }
    }
    
    public Integer getOtherBrowsers() {
        return otherBrowsers;
    }
    
    public void setOtherBrowsers(Integer otherBrowsers) {
        this.otherBrowsers = otherBrowsers;
    }
    
    public void incrementOtherBrowsers() {
        if (this.otherBrowsers == null) {
            this.otherBrowsers = 1;
        } else {
            this.otherBrowsers++;
        }
    }
    
    public Integer getDesktopUsers() {
        return desktopUsers;
    }
    
    public void setDesktopUsers(Integer desktopUsers) {
        this.desktopUsers = desktopUsers;
    }
    
    public void incrementDesktopUsers() {
        if (this.desktopUsers == null) {
            this.desktopUsers = 1;
        } else {
            this.desktopUsers++;
        }
    }
    
    public Integer getMobileUsers() {
        return mobileUsers;
    }
    
    public void setMobileUsers(Integer mobileUsers) {
        this.mobileUsers = mobileUsers;
    }
    
    public void incrementMobileUsers() {
        if (this.mobileUsers == null) {
            this.mobileUsers = 1;
        } else {
            this.mobileUsers++;
        }
    }
    
    public Integer getTabletUsers() {
        return tabletUsers;
    }
    
    public void setTabletUsers(Integer tabletUsers) {
        this.tabletUsers = tabletUsers;
    }
    
    public void incrementTabletUsers() {
        if (this.tabletUsers == null) {
            this.tabletUsers = 1;
        } else {
            this.tabletUsers++;
        }
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public void updateLastUpdated() {
        this.lastUpdated = LocalDateTime.now();
    }
}

