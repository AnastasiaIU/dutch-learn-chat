package com.dutchlearn.service;

import com.dutchlearn.dto.UserLoginDTO;
import com.dutchlearn.dto.UserRegistrationDTO;
import com.dutchlearn.dto.AuthResponseDTO;
import com.dutchlearn.entity.User;
import com.dutchlearn.logging.LogSanitizer;
import com.dutchlearn.repository.UserRepository;
import com.dutchlearn.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AuthService
 * Service for user authentication and registration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user
     */
    public AuthResponseDTO register(UserRegistrationDTO registrationDTO) {
        String maskedEmail = LogSanitizer.maskEmail(registrationDTO.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            log.warn("Registration denied: email already registered email={}", maskedEmail);
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            log.warn("Registration denied: username already taken usernameLength={}",
                    registrationDTO.getUsername() == null ? 0 : registrationDTO.getUsername().length());
            throw new IllegalArgumentException("Username already taken");
        }

        // Create new user
        User user = User.builder()
                .email(registrationDTO.getEmail())
                .username(registrationDTO.getUsername())
                .passwordHash(passwordEncoder.encode(registrationDTO.getPassword()))
                .languageLevel(registrationDTO.getLanguageLevel() != null ? 
                        registrationDTO.getLanguageLevel() : "A2")
            .role(User.UserRole.LEARNER)
                .active(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered userId={} role={} languageLevel={}", user.getId(), user.getRole(), user.getLanguageLevel());

        // Generate token
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());

        return mapToAuthResponse(user, token);
    }

    /**
     * Login user
     */
    public AuthResponseDTO login(UserLoginDTO loginDTO) {
        String maskedEmail = LogSanitizer.maskEmail(loginDTO.getEmail());

        User user = userRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            log.warn("Login denied: invalid password email={}", maskedEmail);
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        log.info("User logged in userId={} role={}", user.getId(), user.getRole());

        // Generate token
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());

        return mapToAuthResponse(user, token);
    }

    /**
     * Map User entity to AuthResponseDTO
     */
    private AuthResponseDTO mapToAuthResponse(User user, String token) {
        return AuthResponseDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .token(token)
                .languageLevel(user.getLanguageLevel())
            .role(user.getRole().name())
                .build();
    }

    /**
     * Get user by email
     */
    public User getUserByEmail(String email) {
        log.debug("Loading user by email={}", LogSanitizer.maskEmail(email));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
