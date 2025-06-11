package com.main.codedrill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @Getter
    @Setter
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false)
    private String title;

    @Getter
    @Setter
    @Column(nullable = false)
    private String description;

    @Getter
    @Setter
    @ElementCollection
    @CollectionTable(name = "task_tags", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String content;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String solution;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String expectedOutput;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String junitTests;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private TaskDifficulty difficulty;

    @Getter
    @Setter
    private Integer xpReward;

    @Getter
    @Setter
    @Column(name = "estimated_time")
    private String estimatedTime; // e.g., "30 min", "1 hour"

    @Getter
    @Setter
    @Column(name = "learning_category")
    private String learningCategory; // FUNDAMENTALS, OOP, DATA_STRUCTURES, ALGORITHMS

    @Getter
    @Setter
    @ManyToOne
    @JoinColumn(name = "in_chapter_id")
    private Chapter inChapter;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnoreProperties({"createdTasks", "password", "hibernateLazyInitializer", "handler"})
    private User createdBy;

    @Getter
    @Setter
    @Column
    private LocalDateTime createdAt;

    @Getter
    @Setter
    @Column
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.REMOVE)
    private List<UserTaskCompletion> completions;

    public Task() {
    }

    public Task(Long id, String title, String description, String content) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.content = content;
    }

    public Task(Long id, String title, String description, String content, String solution) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.content = content;
        this.solution = solution;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TaskDifficulty {
        EASY, MEDIUM, HARD
    }
}
