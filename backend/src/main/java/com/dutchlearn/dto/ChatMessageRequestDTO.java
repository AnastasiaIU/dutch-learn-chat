package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChatMessageRequestDTO
 * Data Transfer Object for sending a new message
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequestDTO {
    private Long sessionId;
    private String userMessage;
    private String language; // nl, en, or auto-detect
}
