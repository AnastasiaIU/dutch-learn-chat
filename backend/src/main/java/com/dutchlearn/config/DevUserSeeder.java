package com.dutchlearn.config;

import com.dutchlearn.entity.User;
import com.dutchlearn.logging.LogSanitizer;
import com.dutchlearn.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DevUserSeeder
 * Seeds local test users when running the dev profile.
 */
@Slf4j
@Component
@Profile("dev")
public class DevUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedOrUpdateUser("Joe A", "a2@test.com", "a2", "A2", User.UserRole.LEARNER);
        seedOrUpdateUser("Jane B", "b1@test.com", "b1", "B1", User.UserRole.LEARNER);
        seedOrUpdateUser("Admin User", "admin@test.com", "admin", "B1", User.UserRole.ADMIN);
    }

    private void seedOrUpdateUser(
            String username,
            String email,
            String plainPassword,
            String level,
            User.UserRole role) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        boolean isNew = user.getId() == null;

        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setLanguageLevel(level);
        user.setRole(role);
        user.setActive(true);

        userRepository.save(user);

        String maskedEmail = LogSanitizer.maskEmail(email);
        if (isNew) {
            log.info("Seeded test user: {} ({})", username, maskedEmail);
        } else {
            log.info("Updated test user: {} ({})", username, maskedEmail);
        }
    }
}