package com.main.apcsataskwebsite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ADMIN, MODERATOR, USER

    @Column
    private String fullName;
    
    @Column(name = "using_temp_password")
    private Boolean usingTempPassword = false;
    
    @Column(name = "last_password_reset_date")
    private LocalDateTime lastPasswordResetDate;
    
    @Column
    private LocalDateTime registrationDate;

    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("createdBy")
    private Set<Task> createdTasks = new HashSet<>();

    // Constructors
    public User() {
        this.registrationDate = LocalDateTime.now();
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.registrationDate = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public boolean isUsingTempPassword() {
        return usingTempPassword != null && usingTempPassword;
    }
    
    public void setUsingTempPassword(Boolean usingTempPassword) {
        this.usingTempPassword = usingTempPassword;
    }
    
    public LocalDateTime getLastPasswordResetDate() {
        return lastPasswordResetDate;
    }
    
    public void setLastPasswordResetDate(LocalDateTime lastPasswordResetDate) {
        this.lastPasswordResetDate = lastPasswordResetDate;
    }
    
    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }
    
    public void setRegistrationDate(LocalDateTime registrationDate) {
        this.registrationDate = registrationDate;
    }

    public Set<Task> getCreatedTasks() {
        return createdTasks;
    }

    public void setCreatedTasks(Set<Task> createdTasks) {
        this.createdTasks = createdTasks;
    }

    // Helper methods
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
    
    public boolean isModerator() {
        return "MODERATOR".equals(role);
    }
    
    public boolean isUser() {
        return "USER".equals(role);
    }
} 