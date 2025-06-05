package com.main.codedrill.repository;

import com.main.codedrill.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRole(String role);

    Long countByRole(String role);

    List<User> findByUsernameContainingIgnoreCase(String query);

    @Query("SELECT u FROM User u ORDER BY u.id")
    List<User> findAllOrderById();
}

