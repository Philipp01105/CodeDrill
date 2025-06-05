package com.main.codedrill.repository;

import com.main.codedrill.model.SystemAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemAnalyticsRepository extends JpaRepository<SystemAnalytics, Long> {

    Optional<SystemAnalytics> findByDate(LocalDate date);

    List<SystemAnalytics> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<SystemAnalytics> findTop30ByOrderByDateDesc();

    @Query("SELECT SUM(sa.activeUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumActiveUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.newUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumNewUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.totalSessions) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumTotalSessionsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT AVG(sa.averageSessionTimeSeconds) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate AND sa.averageSessionTimeSeconds IS NOT NULL")
    Double avgSessionTimeBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.totalTaskViews) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumTaskViewsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.totalTaskAttempts) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumTaskAttemptsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.totalTaskCompletions) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumTaskCompletionsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT AVG(sa.successRate) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate AND sa.successRate IS NOT NULL")
    Double avgSuccessRateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT sa.date, sa.activeUsers FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate ORDER BY sa.date")
    List<Object[]> findActiveUsersTrendBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT sa.date, sa.totalTaskCompletions FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate ORDER BY sa.date")
    List<Object[]> findTaskCompletionsTrendBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.chromeUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumChromeUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.firefoxUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumFirefoxUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.safariUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumSafariUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.edgeUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumEdgeUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.otherBrowsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumOtherBrowsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.desktopUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumDesktopUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.mobileUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumMobileUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(sa.tabletUsers) FROM SystemAnalytics sa WHERE sa.date BETWEEN :startDate AND :endDate")
    Integer sumTabletUsersBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}