package com.main.codedrill.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_attempts")
public class TaskAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "attempt_time", nullable = false)
    private LocalDateTime attemptTime;

    @Column(name = "time_spent_seconds")
    private Long timeSpentSeconds;

    @Column(name = "code_submitted", columnDefinition = "TEXT")
    private String codeSubmitted;

    @Column(name = "successful")
    private Boolean successful;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "session_id")
    private String sessionId;

    // Constructors
    public TaskAttempt() {
        this.attemptTime = LocalDateTime.now();
    }

    public TaskAttempt(User user, Task task, String codeSubmitted, Boolean successful, String sessionId) {
        this.user = user;
        this.task = task;
        this.attemptTime = LocalDateTime.now();
        this.codeSubmitted = codeSubmitted;
        this.successful = successful;
        this.sessionId = sessionId;
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

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public LocalDateTime getAttemptTime() {
        return attemptTime;
    }

    public void setAttemptTime(LocalDateTime attemptTime) {
        this.attemptTime = attemptTime;
    }

    public Long getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Long timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public String getCodeSubmitted() {
        return codeSubmitted;
    }

    public void setCodeSubmitted(String codeSubmitted) {
        this.codeSubmitted = codeSubmitted;
    }

    public Boolean getSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}