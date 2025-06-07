package com.main.codedrill.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_stats")
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private Integer longestStreak = 0;

    @Column(name = "total_xp", nullable = false)
    private Integer totalXp = 0;

    @Column(name = "weekly_xp", nullable = false)
    private Integer weeklyXp = 0;

    @Column(name = "badge_count", nullable = false)
    private Integer badgeCount = 0;

    @Column(name = "tasks_completed", nullable = false)
    private Integer tasksCompleted = 0;

    @Column(name = "overall_progress", nullable = false)
    private Integer overallProgress = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public UserStats() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.weekStartDate = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
    }

    public UserStats(User user) {
        this();
        this.user = user;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business methods
    public void addXp(int xp) {
        this.totalXp += xp;
        this.weeklyXp += xp;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStreak() {
        LocalDate today = LocalDate.now();

        if (lastActivityDate == null) {
            currentStreak = 1;
        } else if (lastActivityDate.equals(today.minusDays(1))) {
            // Consecutive day
            currentStreak++;
        } else if (!lastActivityDate.equals(today)) {
            // Streak broken
            currentStreak = 1;
        }
        // If lastActivityDate equals today, don't change streak

        if (currentStreak > longestStreak) {
            longestStreak = currentStreak;
        }

        lastActivityDate = today;
        this.updatedAt = LocalDateTime.now();
    }

    public void resetWeeklyXp() {
        this.weeklyXp = 0;
        this.weekStartDate = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementTasksCompleted() {
        this.tasksCompleted++;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementBadgeCount() {
        this.badgeCount++;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(Integer currentStreak) { this.currentStreak = currentStreak; }

    public Integer getLongestStreak() { return longestStreak; }
    public void setLongestStreak(Integer longestStreak) { this.longestStreak = longestStreak; }

    public Integer getTotalXp() { return totalXp; }
    public void setTotalXp(Integer totalXp) { this.totalXp = totalXp; }

    public Integer getWeeklyXp() { return weeklyXp; }
    public void setWeeklyXp(Integer weeklyXp) { this.weeklyXp = weeklyXp; }

    public Integer getBadgeCount() { return badgeCount; }
    public void setBadgeCount(Integer badgeCount) { this.badgeCount = badgeCount; }

    public Integer getTasksCompleted() { return tasksCompleted; }
    public void setTasksCompleted(Integer tasksCompleted) { this.tasksCompleted = tasksCompleted; }

    public Integer getOverallProgress() { return overallProgress; }
    public void setOverallProgress(Integer overallProgress) { this.overallProgress = overallProgress; }

    public LocalDate getLastActivityDate() { return lastActivityDate; }
    public void setLastActivityDate(LocalDate lastActivityDate) { this.lastActivityDate = lastActivityDate; }

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}