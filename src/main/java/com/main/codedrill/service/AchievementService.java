package com.main.codedrill.service;

import com.main.codedrill.model.Achievement;
import com.main.codedrill.model.User;
import com.main.codedrill.model.UserAchievement;
import com.main.codedrill.model.UserStats;
import com.main.codedrill.repository.AchievementRepository;
import com.main.codedrill.repository.UserAchievementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserService userService;

    @Autowired
    public AchievementService(AchievementRepository achievementRepository,
                              UserAchievementRepository userAchievementRepository,
                              UserService userService) {
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.userService = userService;
    }

    public List<Map<String, Object>> getUserAchievements(Long userId) {
        // Finde den User
        User user = userService.findById(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        // Hole nur existierende UserAchievements aus der DB
        List<UserAchievement> userAchievements = userAchievementRepository.findByUser(user);

        // Wenn keine Achievements vorhanden, return empty list
        if (userAchievements.isEmpty()) {
            return new ArrayList<>();
        }

        return userAchievements.stream().map(ua -> {
            Map<String, Object> achievementData = new HashMap<>();
            Achievement achievement = ua.getAchievement();

            achievementData.put("name", achievement.getName());
            achievementData.put("description", achievement.getDescription());
            achievementData.put("icon", achievement.getIcon());
            achievementData.put("earned", ua.getIsCompleted());
            achievementData.put("inProgress", ua.isInProgress());
            achievementData.put("progress", ua.getProgressPercentage());
            achievementData.put("progressText", ua.getProgressText());

            if (ua.getIsCompleted()) {
                achievementData.put("earnedDate", "Earned " + ua.getEarnedAt().toLocalDate().toString());
            } else {
                achievementData.put("earnedDate", null);
            }

            return achievementData;
        }).toList();
    }

    @Transactional
    public void initializeDefaultAchievements() {
        if (achievementRepository.count() == 0) {
            List<Achievement> defaultAchievements = createDefaultAchievements();
            achievementRepository.saveAll(defaultAchievements);
        }
    }

    private List<Achievement> createDefaultAchievements() {
        List<Achievement> achievements = new ArrayList<>();

        // Streak achievements
        achievements.add(new Achievement("First Steps", "Complete your first task", "fa-baby", "COMPLETION", 1, 50));
        achievements.add(new Achievement("Week Warrior", "Practice for 7 consecutive days", "fa-fire", "STREAK", 7, 200));
        achievements.add(new Achievement("Code Master", "Complete 50 coding challenges", "fa-code", "COMPLETION", 50, 500));
        achievements.add(new Achievement("Java Scholar", "Complete all beginner topics", "fa-graduation-cap", "SPECIAL", null, 300));

        return achievements;
    }

    public List<Achievement> checkAndAwardAchievements(User user, UserStats stats) {
        List<Achievement> newlyEarned = new ArrayList<>();
        List<Achievement> allAchievements = achievementRepository.findByIsActiveTrue();

        for (Achievement achievement : allAchievements) {
            UserAchievement userAchievement = userAchievementRepository
                    .findByUserAndAchievement(user, achievement)
                    .orElse(new UserAchievement(user, achievement));

            if (!userAchievement.getIsCompleted()) {
                boolean shouldAward = checkAchievementCriteria(achievement, stats);

                if (shouldAward) {
                    userAchievement.setIsCompleted(true);
                    userAchievementRepository.save(userAchievement);
                    stats.incrementBadgeCount();
                    newlyEarned.add(achievement);
                }
            }
        }

        return newlyEarned;
    }

    private boolean checkAchievementCriteria(Achievement achievement, UserStats stats) {
        switch (achievement.getAchievementType()) {
            case "STREAK":
                return stats.getCurrentStreak() >= achievement.getTargetValue();
            case "COMPLETION":
                return stats.getTasksCompleted() >= achievement.getTargetValue();
            case "XP":
                return stats.getTotalXp() >= achievement.getTargetValue();
            default:
                return false;
        }
    }

    // Neue Methode um Achievements für einen User zu initialisieren
    @Transactional
    public void initializeUserAchievements(User user) {
        List<Achievement> allAchievements = achievementRepository.findByIsActiveTrue();

        for (Achievement achievement : allAchievements) {
            // Prüfe ob User dieses Achievement bereits hat
            if (!userAchievementRepository.findByUserAndAchievement(user, achievement).isPresent()) {
                UserAchievement userAchievement = new UserAchievement(user, achievement);
                userAchievementRepository.save(userAchievement);
            }
        }
    }

    public List<Achievement> getAllAchievements() {
        return achievementRepository.findAll();
    }

    public Achievement getAchievementById(Long id) {
        return achievementRepository.findById(id).orElse(null);
    }

    @Transactional
    public Achievement createAchievement(Achievement achievement) {
        // Validierung
        if (achievement.getName() == null || achievement.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Achievement name is required");
        }
        if (achievement.getDescription() == null || achievement.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Achievement description is required");
        }
        if (achievement.getIcon() == null || achievement.getIcon().trim().isEmpty()) {
            throw new IllegalArgumentException("Achievement icon is required");
        }
        if (achievement.getAchievementType() == null || achievement.getAchievementType().trim().isEmpty()) {
            throw new IllegalArgumentException("Achievement type is required");
        }

        // Prüfe ob Name bereits existiert
        if (achievementRepository.findByName(achievement.getName()).isPresent()) {
            throw new IllegalArgumentException("Achievement with this name already exists");
        }

        achievement.setIsActive(true);
        achievement.setCreatedAt(LocalDateTime.now());

        return achievementRepository.save(achievement);
    }

    @Transactional
    public Achievement updateAchievement(Achievement achievement) {
        Achievement existing = achievementRepository.findById(achievement.getId())
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found"));

        // Prüfe ob Name bereits von anderem Achievement verwendet wird
        Achievement existingByName = achievementRepository.findByName(achievement.getName()).orElse(null);
        if (existingByName != null && !existingByName.getId().equals(achievement.getId())) {
            throw new IllegalArgumentException("Achievement with this name already exists");
        }

        existing.setName(achievement.getName());
        existing.setDescription(achievement.getDescription());
        existing.setIcon(achievement.getIcon());
        existing.setAchievementType(achievement.getAchievementType());
        existing.setTargetValue(achievement.getTargetValue());
        existing.setXpReward(achievement.getXpReward());

        return achievementRepository.save(existing);
    }

    @Transactional
    public void deleteAchievement(Long id) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found"));

        // Lösche zuerst alle UserAchievements die darauf verweisen
        userAchievementRepository.deleteByAchievement(achievement);

        // Dann lösche das Achievement selbst
        achievementRepository.delete(achievement);
    }

    @Transactional
    public void toggleAchievementStatus(Long id) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found"));

        achievement.setIsActive(!achievement.getIsActive());
        achievementRepository.save(achievement);
    }
}