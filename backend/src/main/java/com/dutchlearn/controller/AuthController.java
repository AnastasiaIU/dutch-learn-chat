package com.dutchlearn.controller;

import com.dutchlearn.dto.AuthResponseDTO;
import com.dutchlearn.dto.UserLoginDTO;
import com.dutchlearn.dto.UserRegistrationDTO;
import com.dutchlearn.logging.LogSanitizer;
import com.dutchlearn.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController
 * REST Controller for authentication endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody UserRegistrationDTO registrationDTO) {
        String maskedEmail = LogSanitizer.maskEmail(registrationDTO.getEmail());
        log.info("Register request received for email={}", maskedEmail);
        try {
            AuthResponseDTO response = authService.register(registrationDTO);
            log.info("Register succeeded for userId={} role={}", response.getUserId(), response.getRole());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Register rejected for email={} reason={}", maskedEmail, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Register failed unexpectedly for email={}", maskedEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Login user
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody UserLoginDTO loginDTO) {
        String maskedEmail = LogSanitizer.maskEmail(loginDTO.getEmail());
        log.info("Login request received for email={}", maskedEmail);
        try {
            AuthResponseDTO response = authService.login(loginDTO);
            log.info("Login succeeded for userId={} role={}", response.getUserId(), response.getRole());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Login rejected for email={} reason={}", maskedEmail, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Login failed unexpectedly for email={}", maskedEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.debug("Health check requested");
        return ResponseEntity.ok("Backend is running");
    }
}
