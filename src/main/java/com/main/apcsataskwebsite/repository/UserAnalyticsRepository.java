package com.main.apcsataskwebsite.repository;

import com.main.apcsataskwebsite.model.User;
import com.main.apcsataskwebsite.model.UserAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAnalyticsRepository extends JpaRepository<UserAnalytics, Long> {
    
    List<UserAnalytics> findByUser(User user);
    
    List<UserAnalytics> findByUserOrderByLoginTimeDesc(User user);
    
    Optional<UserAnalytics> findBySessionId(String sessionId);
    
    @Query("SELECT ua FROM UserAnalytics ua WHERE ua.user = :user AND ua.logoutTime IS NULL")
    List<UserAnalytics> findActiveSessionsByUser(@Param("user") User user);
    
    @Query("SELECT ua FROM UserAnalytics ua WHERE ua.loginTime >= :startDate AND ua.loginTime <= :endDate")
    List<UserAnalytics> findByLoginTimeBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(DISTINCT ua.user) FROM UserAnalytics ua WHERE ua.loginTime >= :startDate AND ua.loginTime <= :endDate")
    Long countUniqueUsersBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT AVG(ua.timeSpentSeconds) FROM UserAnalytics ua WHERE ua.timeSpentSeconds IS NOT NULL AND ua.loginTime >= :startDate AND ua.loginTime <= :endDate")
    Double averageSessionTimeBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(ua.tasksCompleted) FROM UserAnalytics ua WHERE ua.loginTime >= :startDate AND ua.loginTime <= :endDate")
    Integer totalTasksCompletedBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT ua.browserInfo, COUNT(ua) FROM UserAnalytics ua WHERE ua.loginTime >= :startDate AND ua.loginTime <= :endDate GROUP BY ua.browserInfo")
    List<Object[]> countByBrowserBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT ua.deviceType, COUNT(ua) FROM UserAnalytics ua WHERE ua.loginTime >= :startDate AND ua.loginTime <= :endDate GROUP BY ua.deviceType")
    List<Object[]> countByDeviceTypeBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);

    List<UserAnalytics> findByUserAndLoginTimeAfter(User user, LocalDateTime twentyFourHoursAgo);
}