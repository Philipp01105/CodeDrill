package com.main.codedrill.repository;

import com.main.codedrill.model.LearningPath;
import com.main.codedrill.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    Optional<LearningPath> findByUser(User user);

    Optional<LearningPath> findByUserId(Long userId);
}