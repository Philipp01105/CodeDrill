package com.main.apcsataskwebsite.repository;

import com.main.apcsataskwebsite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRole(String role);
    Long countByRole(String role);
    List<User> findByUsernameContainingIgnoreCase(String query);
} 