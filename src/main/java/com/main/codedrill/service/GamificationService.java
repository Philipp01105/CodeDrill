package com.main.codedrill.service;

import com.main.codedrill.model.*;
import com.main.codedrill.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class GamificationService {

    private final UserStatsRepository userStatsRepository;
    private final AchievementService achievementService;
    private final UserAchievementRepository userAchievementRepository;
    private final LearningPathRepository learningPathRepository;
    private final UserTaskCompletionRepository userTaskCompletionRepository;
    private final TaskRepository taskRepository;

    @Autowired
    public GamificationService(
            UserStatsRepository userStatsRepository,
            AchievementRepository achievementRepository,
            UserAchievementRepository userAchievementRepository,
            LearningPathRepository learningPathRepository,
            UserTaskCompletionRepository userTaskCompletionRepository,
            AchievementService achievementService,
            TaskRepository taskRepository) {
        this.userStatsRepository = userStatsRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.learningPathRepository = learningPathRepository;
        this.userTaskCompletionRepository = userTaskCompletionRepository;
        this.achievementService = achievementService;
        this.taskRepository = taskRepository;
    }


    private void updateLearningPathProgress(LearningPath learningPath, Task task) {
        // TODO: Implement logic to update learning path progress based on task completion
    }

    private void updateOverallProgress(UserStats stats, User user) {
        // Calculate overall progress based on completed tasks
        long totalTasks = taskRepository.count();
        long completedTasks = userTaskCompletionRepository.countByUser(user);
        int progress = (int) Math.min(100, (completedTasks * 100) / totalTasks);
        stats.setOverallProgress(progress);
    }

    private List<Achievement> checkAndAwardAchievements(User user, UserStats stats) {
        // TODO: Implement logic to check for new achievements based on user stats
        // This is a simplified implementation
        // You would check all achievements and award new ones
        return List.of(); // Return list of newly earned achievements
    }

    public List<Map<String, Object>> getUserAchievements(User user) {
        List<UserAchievement> userAchievements = userAchievementRepository.findByUser(user);

        // Wenn der User noch keine Achievements hat, return empty list
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
    public void resetWeeklyStats() {
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        List<UserStats> usersToReset = userStatsRepository.findUsersNeedingWeeklyReset(weekStart);

        for (UserStats stats : usersToReset) {
            stats.resetWeeklyXp();
            userStatsRepository.save(stats);
        }
    }


    @Transactional
    public Map<String, Object> processTaskCompletion(User user, Task task, boolean isFirstCompletion) {
        Map<String, Object> result = new HashMap<>();

        if (!isFirstCompletion) {
            result.put("taskCompleted", false);
            result.put("message", "Task already completed");
            return result;
        }

        // Initialize user stats if this is their first task completion
        UserStats stats = initializeUserStats(user);
        LearningPath learningPath = initializeLearningPath(user);

        // Initialize achievements if they don't exist yet
        achievementService.initializeUserAchievements(user);

        // Award XP for first completion
        int xpEarned = task.getXpReward() != null ? task.getXpReward() : 50;
        stats.addXp(xpEarned);
        stats.incrementTasksCompleted();
        stats.updateStreak();

        // Update learning path progress
        updateLearningPathProgress(learningPath, task);

        // Update overall progress
        updateOverallProgress(stats, user);

        // Check for new achievements
        List<Achievement> newAchievements = achievementService.checkAndAwardAchievements(user, stats);

        // Save updates
        userStatsRepository.save(stats);
        learningPathRepository.save(learningPath);

        result.put("xpEarned", xpEarned);
        result.put("newAchievements", newAchievements);
        result.put("taskCompleted", true);
        result.put("correct", true);
        result.put("updatedStats", stats);
        result.put("updatedLearningPath", learningPath);

        return result;
    }

    public List<Map<String, Object>> getWeeklyLeaderboard() {
        List<UserStats> topUsers = userStatsRepository.findAllOrderByWeeklyXpDesc();

        return topUsers.stream().limit(10).map(stats -> {
            Map<String, Object> leaderboardEntry = new HashMap<>();
            leaderboardEntry.put("username", stats.getUser().getUsername());
            leaderboardEntry.put("weeklyXp", stats.getWeeklyXp());
            leaderboardEntry.put("currentStreak", stats.getCurrentStreak());
            leaderboardEntry.put("totalXp", stats.getTotalXp());
            return leaderboardEntry;
        }).toList();
    }

    // Überarbeite auch die getLearningPath Methode:
    public LearningPath getLearningPath(User user) {
        return learningPathRepository.findByUser(user).orElse(null);
    }

    public LearningPath getLearningPath(Long userId) {
        // Nur return wenn bereits existiert, sonst null
        return learningPathRepository.findByUserId(userId).orElse(null);
    }

    // Neue Methode um Learning Path zu initialisieren (nur wenn User eine Task completed)
    public LearningPath initializeLearningPath(User user) {
        return learningPathRepository.findByUser(user).orElseGet(() -> {
            LearningPath newPath = new LearningPath(user, "BEGINNER");
            return learningPathRepository.save(newPath);
        });
    }

    // Neue Methode um User Stats zu initialisieren (nur wenn User eine Task completed)
    public UserStats initializeUserStats(User user) {
        return userStatsRepository.findByUser(user).orElseGet(() -> {
            UserStats newStats = new UserStats(user);
            return userStatsRepository.save(newStats);
        });
    }

    // Überarbeite die getUserStats Methode:
    public UserStats getUserStats(User user) {
        // Nur return wenn bereits existiert, sonst null
        return userStatsRepository.findByUser(user).orElse(null);
    }

    public UserStats getUserStats(Long userId) {
        // Nur return wenn bereits existiert, sonst null
        return userStatsRepository.findByUserId(userId).orElse(null);
    }

    public LearningPath[] getAllLearningPaths() {
        return learningPathRepository.findAll().toArray(new LearningPath[0]);
    }
}