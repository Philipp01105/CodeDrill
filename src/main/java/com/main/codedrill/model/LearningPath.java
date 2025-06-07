package com.main.codedrill.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_paths")
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "path_type", nullable = false)
    private String pathType = "BEGINNER";

    @Column(name = "fundamentals_progress", nullable = false)
    private Integer fundamentalsProgress = 0;

    @Column(name = "oop_progress", nullable = false)
    private Integer oopProgress = 0;

    @Column(name = "data_structures_progress", nullable = false)
    private Integer dataStructuresProgress = 0;

    @Column(name = "algorithms_progress", nullable = false)
    private Integer algorithmsProgress = 0;

    @Column(name = "advanced_progress", nullable = false)
    private Integer advancedProgress = 0;

    @Column(name = "current_level", nullable = false)
    private Integer currentLevel = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public LearningPath() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public LearningPath(User user, String pathType) {
        this();
        this.user = user;
        this.pathType = pathType;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business methods
    public void updateProgress(String category, int progress) {
        switch (category.toLowerCase()) {
            case "fundamentals":
                this.fundamentalsProgress = Math.min(100, progress);
                break;
            case "oop":
                this.oopProgress = Math.min(100, progress);
                break;
            case "datastructures":
                this.dataStructuresProgress = Math.min(100, progress);
                break;
            case "algorithms":
                this.algorithmsProgress = Math.min(100, progress);
                break;
            case "advanced":
                this.advancedProgress = Math.min(100, progress);
                break;
        }

        // Update current level based on progress
        updateCurrentLevel();
        this.updatedAt = LocalDateTime.now();
    }

    private void updateCurrentLevel() {
        if (fundamentalsProgress >= 100 && oopProgress >= 100 && dataStructuresProgress >= 100) {
            currentLevel = 4; // Advanced
        } else if (fundamentalsProgress >= 100 && oopProgress >= 100) {
            currentLevel = 3; // Data Structures
        } else if (fundamentalsProgress >= 100) {
            currentLevel = 2; // OOP
        } else {
            currentLevel = 1; // Fundamentals
        }
    }

    public int getOverallProgress() {
        return (fundamentalsProgress + oopProgress + dataStructuresProgress + algorithmsProgress + advancedProgress) / 5;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getPathType() { return pathType; }
    public void setPathType(String pathType) { this.pathType = pathType; }

    public Integer getFundamentalsProgress() { return fundamentalsProgress; }
    public void setFundamentalsProgress(Integer fundamentalsProgress) { this.fundamentalsProgress = fundamentalsProgress; }

    public Integer getOopProgress() { return oopProgress; }
    public void setOopProgress(Integer oopProgress) { this.oopProgress = oopProgress; }

    public Integer getDataStructuresProgress() { return dataStructuresProgress; }
    public void setDataStructuresProgress(Integer dataStructuresProgress) { this.dataStructuresProgress = dataStructuresProgress; }

    public Integer getAlgorithmsProgress() { return algorithmsProgress; }
    public void setAlgorithmsProgress(Integer algorithmsProgress) { this.algorithmsProgress = algorithmsProgress; }

    public Integer getAdvancedProgress() { return advancedProgress; }
    public void setAdvancedProgress(Integer advancedProgress) { this.advancedProgress = advancedProgress; }

    public Integer getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(Integer currentLevel) { this.currentLevel = currentLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}