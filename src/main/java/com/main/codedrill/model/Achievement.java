package com.main.codedrill.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "achievements")
public class Achievement {

    // Getters and Setters
    @Id
    @Getter
    @Setter
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false, unique = true)
    private String name;

    @Getter
    @Setter
    @Column(nullable = false)
    private String description;

    @Getter
    @Setter
    @Column(nullable = false)
    private String icon;

    @Getter
    @Setter
    @Column(name = "achievement_type", nullable = false)
    private String achievementType; // STREAK, COMPLETION, XP, SPECIAL

    @Getter
    @Setter
    @Column(name = "target_value")
    private Integer targetValue;

    @Getter
    @Setter
    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 0;

    @Getter
    @Setter
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Getter
    @Setter
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Constructors
    public Achievement() {
        this.createdAt = LocalDateTime.now();
    }

    public Achievement(String name, String description, String icon, String achievementType, Integer targetValue, Integer xpReward) {
        this();
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.achievementType = achievementType;
        this.targetValue = targetValue;
        this.xpReward = xpReward;
    }
}