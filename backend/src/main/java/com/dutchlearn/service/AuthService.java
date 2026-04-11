package com.dutchlearn.service;

import com.dutchlearn.dto.UserLoginDTO;
import com.dutchlearn.dto.UserRegistrationDTO;
import com.dutchlearn.dto.AuthResponseDTO;
import com.dutchlearn.entity.User;
import com.dutchlearn.repository.UserRepository;
import com.dutchlearn.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AuthService
 * Service for user authentication and registration
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user
     */
    public AuthResponseDTO register(UserRegistrationDTO registrationDTO) {
        // Check if user already exists
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        // Create new user
        User user = User.builder()
                .email(registrationDTO.getEmail())
                .username(registrationDTO.getUsername())
                .passwordHash(passwordEncoder.encode(registrationDTO.getPassword()))
                .languageLevel(registrationDTO.getLanguageLevel() != null ? 
                        registrationDTO.getLanguageLevel() : "A2")
                .active(true)
                .build();

        user = userRepository.save(user);

        // Generate token
        String token = jwtTokenProvider.generateToken(user.getEmail());

        return mapToAuthResponse(user, token);
    }

    /**
     * Login user
     */
    public AuthResponseDTO login(UserLoginDTO loginDTO) {
        User user = userRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Generate token
        String token = jwtTokenProvider.generateToken(user.getEmail());

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
                .build();
    }

    /**
     * Get user by email
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
