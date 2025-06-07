package com.main.codedrill.repository;

import com.main.codedrill.model.User;
import com.main.codedrill.model.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserStatsRepository extends JpaRepository<UserStats, Long> {

    Optional<UserStats> findByUser(User user);

    Optional<UserStats> findByUserId(Long userId);

    @Query("SELECT us FROM UserStats us ORDER BY us.totalXp DESC")
    List<UserStats> findAllOrderByTotalXpDesc();

    @Query("SELECT us FROM UserStats us ORDER BY us.weeklyXp DESC")
    List<UserStats> findAllOrderByWeeklyXpDesc();

    @Query("SELECT us FROM UserStats us WHERE us.weekStartDate < :date")
    List<UserStats> findUsersNeedingWeeklyReset(@Param("date") LocalDate date);

    @Query("SELECT AVG(us.totalXp) FROM UserStats us")
    Double findAverageXp();

    @Query("SELECT MAX(us.currentStreak) FROM UserStats us")
    Integer findMaxStreak();
}