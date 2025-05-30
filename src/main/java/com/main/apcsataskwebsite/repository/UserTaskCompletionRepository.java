package com.main.apcsataskwebsite.repository;

import com.main.apcsataskwebsite.model.Task;
import com.main.apcsataskwebsite.model.User;
import com.main.apcsataskwebsite.model.UserTaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTaskCompletionRepository extends JpaRepository<UserTaskCompletion, Long> {
    
    List<UserTaskCompletion> findByUser(User user);
    
    List<UserTaskCompletion> findByTask(Task task);
    
    Optional<UserTaskCompletion> findByUserAndTask(User user, Task task);
    
    boolean existsByUserAndTask(User user, Task task);
    
    @Query("SELECT utc.task.id FROM UserTaskCompletion utc WHERE utc.user = :user")
    List<Long> findTaskIdsByUser(@Param("user") User user);

    void deleteByTask(Task task);
} 