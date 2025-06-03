package com.main.codedrill.repository;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.TaskAttempt;
import com.main.codedrill.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, Long> {
    
    List<TaskAttempt> findByUser(User user);
    
    List<TaskAttempt> findByTask(Task task);
    
    List<TaskAttempt> findByUserAndTask(User user, Task task);
    
    List<TaskAttempt> findBySessionId(String sessionId);
    
    @Query("SELECT ta FROM TaskAttempt ta WHERE ta.user = :user AND ta.attemptTime >= :startDate AND ta.attemptTime <= :endDate")
    List<TaskAttempt> findByUserAndAttemptTimeBetween(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT ta FROM TaskAttempt ta WHERE ta.task = :task AND ta.attemptTime >= :startDate AND ta.attemptTime <= :endDate")
    List<TaskAttempt> findByTaskAndAttemptTimeBetween(
            @Param("task") Task task,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(ta) FROM TaskAttempt ta WHERE ta.user = :user AND ta.successful = true")
    Long countSuccessfulAttemptsByUser(@Param("user") User user);
    
    @Query("SELECT COUNT(ta) FROM TaskAttempt ta WHERE ta.task = :task AND ta.successful = true")
    Long countSuccessfulAttemptsByTask(@Param("task") Task task);
    
    @Query("SELECT AVG(ta.timeSpentSeconds) FROM TaskAttempt ta WHERE ta.user = :user AND ta.timeSpentSeconds IS NOT NULL")
    Double averageTimeSpentByUser(@Param("user") User user);
    
    @Query("SELECT AVG(ta.timeSpentSeconds) FROM TaskAttempt ta WHERE ta.task = :task AND ta.timeSpentSeconds IS NOT NULL")
    Double averageTimeSpentByTask(@Param("task") Task task);
    
    @Query("SELECT ta.task.id, COUNT(ta) FROM TaskAttempt ta WHERE ta.user = :user GROUP BY ta.task.id ORDER BY COUNT(ta) DESC")
    List<Object[]> findMostAttemptedTasksByUser(@Param("user") User user);
    
    @Query("SELECT ta.user.id, COUNT(ta) FROM TaskAttempt ta WHERE ta.task = :task GROUP BY ta.user.id ORDER BY COUNT(ta) DESC")
    List<Object[]> findUsersByMostAttemptsOnTask(@Param("task") Task task);
    
    @Query("SELECT COUNT(ta) FROM TaskAttempt ta WHERE ta.attemptTime >= :startDate AND ta.attemptTime <= :endDate")
    Long countAttemptsBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(ta) FROM TaskAttempt ta WHERE ta.successful = true AND ta.attemptTime >= :startDate AND ta.attemptTime <= :endDate")
    Long countSuccessfulAttemptsBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    boolean existsByUserAndTaskAndSuccessful(User user, Task task, boolean b);

    List<TaskAttempt> findByUserAndTaskAndSuccessful(User user, Task task, boolean successful);

    void deleteByTask(Task taskToDelete);
}