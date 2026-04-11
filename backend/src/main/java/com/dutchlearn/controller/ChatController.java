package com.dutchlearn.controller;

import com.dutchlearn.dto.ChatMessageDTO;
import com.dutchlearn.dto.ChatMessageRequestDTO;
import com.dutchlearn.dto.ChatMessageResponseDTO;
import com.dutchlearn.entity.ChatSession;
import com.dutchlearn.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ChatController
 * REST Controller for chat endpoints
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
public class ChatController {

    private final ChatService chatService;

    /**
     * Create a new chat session
     */
    @PostMapping("/session")
    public ResponseEntity<ChatSession> createSession(
            @RequestParam Long userId,
            @RequestParam(required = false) String topic) {
        try {
            ChatSession session = chatService.createSession(userId, topic);
            return ResponseEntity.status(HttpStatus.CREATED).body(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Send a message and get AI response
     */
    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponseDTO> sendMessage(
            @RequestBody ChatMessageRequestDTO requestDTO) {
        try {
            ChatMessageResponseDTO response = chatService.sendMessage(requestDTO);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get chat history for a session
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessageDTO>> getChatHistory(@PathVariable Long sessionId) {
        try {
            List<ChatMessageDTO> history = chatService.getChatHistory(sessionId);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Get all sessions for a user
     */
    @GetMapping("/sessions/{userId}")
    public ResponseEntity<List<ChatSession>> getUserSessions(@PathVariable Long userId) {
        try {
            List<ChatSession> sessions = chatService.getUserSessions(userId);
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
