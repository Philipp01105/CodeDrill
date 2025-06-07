package com.main.codedrill.repository;

import com.main.codedrill.model.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    List<Achievement> findByIsActiveTrue();

    List<Achievement> findByAchievementType(String achievementType);

    Optional<Achievement> findByName(String name);

    @Query("SELECT a FROM Achievement a WHERE a.isActive = true ORDER BY a.xpReward DESC")
    List<Achievement> findActiveAchievementsOrderByXpReward();

}