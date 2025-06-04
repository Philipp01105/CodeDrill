package com.main.codedrill.service;

import com.main.codedrill.model.User;
import com.main.codedrill.model.VerificationToken;
import com.main.codedrill.repository.UserRepository;
import com.main.codedrill.repository.VerificationTokenRepository;
import com.main.codedrill.repository.UserTaskCompletionRepository;
import com.main.codedrill.repository.TaskAttemptRepository;
import com.main.codedrill.repository.UserAnalyticsRepository;
import com.main.codedrill.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsService analyticsService;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final UserTaskCompletionRepository userTaskCompletionRepository;
    private final TaskAttemptRepository taskAttemptRepository;
    private final UserAnalyticsRepository userAnalyticsRepository;
    private final TaskRepository taskRepository;

    private final Logger logger = LoggerFactory.getLogger(UserService.class);

    private boolean currentExecutions = false;

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AnalyticsService analyticsService,
                       EmailService emailService,
                       VerificationTokenRepository verificationTokenRepository,
                       UserTaskCompletionRepository userTaskCompletionRepository,
                       TaskAttemptRepository taskAttemptRepository,
                       UserAnalyticsRepository userAnalyticsRepository,
                       TaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.analyticsService = analyticsService;
        this.emailService = emailService;
        this.verificationTokenRepository = verificationTokenRepository;
        this.userTaskCompletionRepository = userTaskCompletionRepository;
        this.taskAttemptRepository = taskAttemptRepository;
        this.userAnalyticsRepository = userAnalyticsRepository;
        this.taskRepository = taskRepository;

        if (userRepository.count() == 0) {
            createAdminUser();
        }
    }

    // WARNING: Reset password after application startup
    private void createAdminUser() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setUsingTempPassword(true);
        admin.setRole("ADMIN");
        admin.setFullName("System Administrator");
        admin.setEmail("admin@example.com"); // Add email for admin
        admin.setEnabled(true); // Admin is enabled by default
        userRepository.save(admin);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
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
    @Transactional
    public User registerUser(String username, String password, String fullName, String email) {
        if (userRepository.existsByUsername(username)) {
            return null;
        }

        if (userRepository.existsByEmail(email)) {
            return null;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setFullName(fullName);
        user.setEmail(email);
        user.setEnabled(false);
        user.setRegistrationDate(LocalDateTime.now());
        analyticsService.trackNewUserRegistration();

        User savedUser = userRepository.save(user);

        VerificationToken token = new VerificationToken(savedUser);
        verificationTokenRepository.save(token);

        try {
            emailService.sendVerificationEmail(
                savedUser.getEmail(),
                "CodeDrill - Verify Your Email Address",
                token.getToken()
            );
        } catch (Exception e) {
            logger.error("Error sending verification email to user: {} - error: {}", savedUser.getEmail(), e.getMessage());
        }

        return savedUser;
    }

    /**
     * Verifies a user based on a token
     * @param token The verification token
     * @return true if verification was successful, false otherwise
     */
    @Transactional
    public boolean verifyUser(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token);

        if (verificationToken == null || verificationToken.isExpired()) {
            return false;
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        verificationTokenRepository.delete(verificationToken);

        return true;
    }

    /**
     * Creates a new verification token for a user
     * @param user The user for whom to create a new token
     * @return The new VerificationToken
     */
    @Transactional
    public VerificationToken generateNewVerificationToken(User user) {
        VerificationToken existingToken = verificationTokenRepository.findByUser(user);
        if (existingToken != null) {
            verificationTokenRepository.delete(existingToken);
        }

        VerificationToken newToken = new VerificationToken(user);
        return verificationTokenRepository.save(newToken);
    }

    public User createModerator(User moderator, User admin) {
        if (admin != null && admin.isAdmin()) {
            if (userRepository.existsByUsername(moderator.getUsername())) {
                return null;
            }

            if (userRepository.existsByEmail(moderator.getEmail())) {
                return null;
            }


            moderator.setRole("MODERATOR");
            moderator.setPassword(passwordEncoder.encode(moderator.getPassword()));
            moderator.setEnabled(true);

            return userRepository.save(moderator);
        }

        return null;
    }

    /**
     * Generate a temporary password for a user
     * @return the temporary password
     */
    public String resetPasswordWithTemp(Long userId, User admin) {
        if (admin != null && admin.isAdmin()) {
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isPresent()) {
                User user = userOpt.get();

                String tempPassword = generateTempPassword();

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
     * Get current number of execution slots in use
     */
    public boolean getCurrentExecutions() {
        return currentExecutions;
    }

    public void setCurrentExecutions(boolean currentExecutions) {
        this.currentExecutions = currentExecutions;;
    }

    /**
     * Generate a temporary password
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 12; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    /**
     * Deletes a user and all associated data
     * @param userId ID of the user to delete
     * @param admin The admin performing the deletion
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean deleteUser(Long userId, User admin) {
        if (admin != null && admin.isAdmin()) {
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isPresent() && !userOpt.get().isAdmin()) {
                User userToDelete = userOpt.get();

                verificationTokenRepository.delete(verificationTokenRepository.findByUser(userToDelete));

                userTaskCompletionRepository.deleteAll(userTaskCompletionRepository.findByUser(userToDelete));

                taskAttemptRepository.deleteAll(taskAttemptRepository.findByUser(userToDelete));

                userAnalyticsRepository.deleteAll(userAnalyticsRepository.findByUser(userToDelete));

                taskRepository.deleteAll(taskRepository.findBycreatedBy(userToDelete));

                userRepository.delete(userToDelete);
                return true;
            }
        }

        return false;
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
        if (user == null) return true;
        return !passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public int countAllUsers() {
        return Math.toIntExact(userRepository.count());
    }

    public int countAllModerators() {
        return Math.toIntExact(userRepository.countByRole("MODERATOR"));
    }

    public User findById(Long userid) {
        if (userid == null) {
            return null;
        }
        return userRepository.findById(userid).orElse(null);
    }

    public User setModerator(User user, User admin) {
        if (admin != null && admin.isAdmin()) {
            if (user == null || user.getId() == null) {
                return null;
            }

            Optional<User> existingUserOpt = userRepository.findById(user.getId());
            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();
                existingUser.setRole("MODERATOR");
                return userRepository.save(existingUser);
            }
        }
        return null;
    }

    public User setUser(User user, User admin) {
        if (admin != null && admin.isAdmin()) {
            if (user == null || user.getId() == null) {
                return null;
            }

            Optional<User> existingUserOpt = userRepository.findById(user.getId());
            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();
                existingUser.setRole("USER");
                return userRepository.save(existingUser);
            }
        }
        return null;
    }
}

