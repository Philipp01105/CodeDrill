package com.main.apcsataskwebsite.service;

import com.main.apcsataskwebsite.model.User;
import com.main.apcsataskwebsite.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    // Maximum number of regular users allowed
    private static final int MAX_USERS = 10;
    
    @Value("${docker.max_concurrent:8}")
    private int maxConcurrentExecutions;
    
    // Track current executions
    private int currentExecutions = 0;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        
        // Initialize admin if no users exist
        if (userRepository.count() == 0) {
            createAdminUser();
        }
    }

    // WARNING: Reset password after application startup
    private void createAdminUser() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setRole("ADMIN");
        admin.setFullName("System Administrator");
        userRepository.save(admin);
    }
    
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
    
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }
    
    public List<User> findAllRegularUsers() {
        return userRepository.findByRole("USER");
    }
    
    public List<User> findAllModerators() {
        return userRepository.findByRole("MODERATOR");
    }
    
    /**
     * Register a new user
     * @return the created user, or null if registration failed
     */
    public User registerUser(String username, String password, String fullName) {
        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            return null;
        }

        
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setFullName(fullName);
        user.setRegistrationDate(LocalDateTime.now());
        
        return userRepository.save(user);
    }
    
    public User createModerator(User moderator, User admin) {
        // Ensure only admin can create moderators
        if (admin != null && admin.isAdmin()) {
            if (userRepository.existsByUsername(moderator.getUsername())) {
                return null; // Username already exists
            }
            
            // Set moderator role and encode password
            moderator.setRole("MODERATOR");
            moderator.setPassword(passwordEncoder.encode(moderator.getPassword()));
            
            return userRepository.save(moderator);
        }
        
        return null;
    }
    
    /**
     * Generate a temporary password for a user
     * @return the temporary password
     */
    public String resetPasswordWithTemp(Long userId, User admin) {
        // Only admin can reset passwords
        if (admin != null && admin.isAdmin()) {
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // Generate a random temporary password
                String tempPassword = generateTempPassword();
                
                // Update user record
                user.setPassword(passwordEncoder.encode(tempPassword));
                user.setUsingTempPassword(true);
                user.setLastPasswordResetDate(LocalDateTime.now());
                userRepository.save(user);
                
                return tempPassword;
            }
        }
        
        return null;
    }
    
    /**
     * Update a user's password (used after login with temp password)
     */
    public boolean updatePassword(User user, String newPassword) {
        if (user != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUsingTempPassword(false);
            user.setLastPasswordResetDate(LocalDateTime.now());
            userRepository.save(user);
            return true;
        }
        return false;
    }
    
    /**
     * Check if a code execution slot is available
     */
    public synchronized boolean acquireExecutionSlot() {
        if (currentExecutions < maxConcurrentExecutions) {
            currentExecutions++;
            return true;
        }
        return false;
    }
    
    /**
     * Release a code execution slot
     */
    public synchronized void releaseExecutionSlot() {
        if (currentExecutions > 0) {
            currentExecutions--;
        }
    }
    
    /**
     * Get current number of execution slots in use
     */
    public int getCurrentExecutions() {
        return currentExecutions;
    }
    
    /**
     * Generate a temporary password
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        // Generate a 12-char password
        for (int i = 0; i < 12; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        
        return sb.toString();
    }
    
    public boolean deleteUser(Long userId, User admin) {
        // Only admin can delete users
        if (admin != null && admin.isAdmin()) {
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isPresent() && !userOpt.get().isAdmin()) {
                userRepository.deleteById(userId);
                return true;
            }
        }
        
        return false;
    }
    
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
    
    /**
     * Search users by username
     */
    public List<User> searchUsersByUsername(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query);
    }
    
    /**
     * Validate if a raw password matches the encoded password for a user
     */
    public boolean validatePassword(User user, String rawPassword) {
        if (user == null) return false;
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public int countAllUsers() {
        return Math.toIntExact(userRepository.count());
    }

    public int countAllModerators() {
        return Math.toIntExact(userRepository.countByRole("MODERATOR"));
    }
}