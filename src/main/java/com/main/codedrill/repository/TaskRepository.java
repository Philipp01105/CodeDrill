package com.main.codedrill.repository;

import com.main.codedrill.model.Task;
import com.main.codedrill.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByOrderByCreatedAtDesc();
    
    @Query("SELECT t FROM Task t JOIN t.tags tag WHERE tag = :tag ORDER BY t.createdAt DESC")
    List<Task> findByTagsContainingOrderByCreatedAtDesc(@Param("tag") String tag);
    
    List<Task> findByCreatedByOrderByCreatedAtDesc(User user);

    Iterable<? extends Task> findBycreatedBy(User user);
}