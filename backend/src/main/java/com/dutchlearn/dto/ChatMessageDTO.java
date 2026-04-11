package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChatMessageDTO
 * Data Transfer Object for chat messages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
    private Long id;
    private String role; // USER or ASSISTANT
    private String content;
    private String languageUsed;
    private String createdAt;
}
