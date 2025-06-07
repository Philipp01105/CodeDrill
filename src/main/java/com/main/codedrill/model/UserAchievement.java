package com.main.codedrill.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_achievements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "achievement_id"})
})
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    @Column(name = "current_progress", nullable = false)
    private Integer currentProgress = 0;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    // Constructors
    public UserAchievement() {
        this.earnedAt = LocalDateTime.now();
    }

    public UserAchievement(User user, Achievement achievement) {
        this();
        this.user = user;
        this.achievement = achievement;
    }

    // Business methods
    public void updateProgress(int progress) {
        this.currentProgress = progress;
        if (achievement.getTargetValue() != null && progress >= achievement.getTargetValue()) {
            this.isCompleted = true;
            this.earnedAt = LocalDateTime.now();
        }
    }

    public boolean isInProgress() {
        return !isCompleted && currentProgress > 0;
    }

    public int getProgressPercentage() {
        if (achievement.getTargetValue() == null || achievement.getTargetValue() == 0) {
            return isCompleted ? 100 : 0;
        }
        return Math.min(100, (currentProgress * 100) / achievement.getTargetValue());
    }

    public String getProgressText() {
        if (achievement.getTargetValue() == null) {
            return isCompleted ? "Completed" : "In Progress";
        }
        return currentProgress + "/" + achievement.getTargetValue();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Achievement getAchievement() { return achievement; }
    public void setAchievement(Achievement achievement) { this.achievement = achievement; }

    public LocalDateTime getEarnedAt() { return earnedAt; }
    public void setEarnedAt(LocalDateTime earnedAt) { this.earnedAt = earnedAt; }

    public Integer getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(Integer currentProgress) { this.currentProgress = currentProgress; }

    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }
}