package com.main.codedrill.service;

import com.main.codedrill.model.User;
import com.main.codedrill.model.VerificationToken;
import com.main.codedrill.repository.UserRepository;
import com.main.codedrill.repository.VerificationTokenRepository;
import com.main.codedrill.utility.VerificationCodeGenerator;
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

    // Track current executions
    private boolean currentExecutions = false;

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AnalyticsService analyticsService,
                       EmailService emailService,
                       VerificationTokenRepository verificationTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.analyticsService = analyticsService;
        this.emailService = emailService;
        this.verificationTokenRepository = verificationTokenRepository;

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
        admin.setEnabled(true);
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
        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            return null;
        }

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            return null;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setFullName(fullName);
        user.setEmail(email);
        user.setEnabled(false); // Nutzer ist erst nach E-Mail-Verifizierung aktiviert
        user.setRegistrationDate(LocalDateTime.now());
        analyticsService.trackNewUserRegistration();

        User savedUser = userRepository.save(user);

        // Verifizierungscode generieren und speichern
        String verificationCode = VerificationCodeGenerator.generateVerificationCode();
        VerificationToken token = new VerificationToken(verificationCode, savedUser);
        verificationTokenRepository.save(token);

        // E-Mail mit Verifizierungscode senden
        emailService.sendVerificationEmail(email, verificationCode);

        return savedUser;
    }

    /**
     * Verifiziert die E-Mail-Adresse eines Nutzers anhand des Verifizierungscodes
     */
    @Transactional
    public boolean verifyEmail(String verificationCode) {
        VerificationToken token = verificationTokenRepository.findByToken(verificationCode);

        if (token == null || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            return false;
        }

        User user = token.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        // Token nach erfolgreicher Verifizierung löschen
        verificationTokenRepository.delete(token);

        return true;
    }

    /**
     * Sendet einen neuen Verifizierungscode, falls der Nutzer noch nicht verifiziert ist
     */
    @Transactional
    public boolean resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && !user.isEnabled()) {
            VerificationToken token = verificationTokenRepository.findByUser(user);

            // Alten Token löschen und neuen erstellen
            if (token != null) {
                verificationTokenRepository.delete(token);
            }

            String newCode = VerificationCodeGenerator.generateVerificationCode();
            verificationTokenRepository.save(new VerificationToken(newCode, user));
            emailService.sendVerificationEmail(email, newCode);
            return true;
        }
        return false;
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
            moderator.setEnabled(true); // Moderatoren werden direkt aktiviert

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
     * Get current number of execution slots in use
     */
    public boolean getCurrentExecutions() {
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