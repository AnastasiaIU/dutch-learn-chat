package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserRegistrationDTO
 * Data Transfer Object for user registration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegistrationDTO {
    private String email;
    private String username;
    private String password;
    private String languageLevel; // A2 or B1
}
