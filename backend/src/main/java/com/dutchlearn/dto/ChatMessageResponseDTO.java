package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChatMessageResponseDTO
 * Data Transfer Object for chat message response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponseDTO {
    private Long sessionId;
    private ChatMessageDTO userMessage;
    private ChatMessageDTO assistantMessage;
}
