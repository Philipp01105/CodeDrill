package com.main.codedrill.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chapters")
public class Chapter {

    @Id
    @Getter
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(name = "title", nullable = false)
    private String title;

    @Getter
    @Setter
    @Column(name = "description", nullable = false)
    private String description;

    @Getter
    @Setter
    @Column(name = "difficulty", nullable = false)
    private difficultyLevel difficulty;

    @Getter
    @Setter
    @Column(name = "timeEstimate", nullable = false)
    private Integer timeEstimate;

    @Getter
    @Setter
    @Column(name = "xpReward", nullable = false)
    private Integer xpReward;

    @Getter
    @Setter
    @Column(name = "isCompleted", nullable = false)
    private Boolean isCompleted = false;

    @Getter
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @Column(name = "tasks", nullable = false)
    private List<Task> tasks;

    @Getter
    @Setter
    @Column(name = "progress", nullable = false)
    private Double progress = 0.0;

    @Getter
    @Setter
    @ManyToOne
    @JoinColumn(name = "learning_path_id")
    private LearningPath learningPath;

    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Getter
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Chapter(String title, String description, difficultyLevel difficulty, Integer timeEstimate, Integer xpReward) {
        this.title = title;
        this.description = description;
        this.difficulty = difficulty;
        this.timeEstimate = timeEstimate;
        this.xpReward = xpReward;
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addTask(Task task) {
        if (this.tasks == null) {
            this.tasks = new ArrayList<>();
        }
        if(this.tasks.contains(task)) {
            return;
        }
        task.setInChapter(this); // Set the chapter for the task
        this.tasks.add(task);
    }

    public enum difficultyLevel {
        EASY, MEDIUM, HARD
    }
}
