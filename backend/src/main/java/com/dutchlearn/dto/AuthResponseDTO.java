package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponseDTO
 * Data Transfer Object for authentication response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {
    private Long userId;
    private String username;
    private String email;
    private String token;
    private String languageLevel;
    private String role;
}
