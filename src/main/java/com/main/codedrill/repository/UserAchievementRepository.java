package com.main.codedrill.repository;

import com.main.codedrill.model.Achievement;
import com.main.codedrill.model.User;
import com.main.codedrill.model.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    List<UserAchievement> findByUser(User user);

    List<UserAchievement> findByUserAndIsCompletedTrue(User user);

    List<UserAchievement> findByUserAndIsCompletedFalse(User user);

    Optional<UserAchievement> findByUserAndAchievement(User user, Achievement achievement);

    @Query("SELECT ua FROM UserAchievement ua WHERE ua.user = :user AND ua.isCompleted = false AND ua.currentProgress > 0")
    List<UserAchievement> findInProgressAchievements(@Param("user") User user);

    @Query("SELECT COUNT(ua) FROM UserAchievement ua WHERE ua.user = :user AND ua.isCompleted = true")
    Long countCompletedAchievements(@Param("user") User user);

    void deleteByAchievement(Achievement achievement);
}