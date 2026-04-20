package com.dutchlearn.controller;

import com.dutchlearn.dto.ChatMessageDTO;
import com.dutchlearn.dto.ChatMessageRequestDTO;
import com.dutchlearn.dto.ChatMessageResponseDTO;
import com.dutchlearn.entity.ChatSession;
import com.dutchlearn.logging.LogSanitizer;
import com.dutchlearn.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ChatController {

    private final ChatService chatService;

    /**
     * Create a new chat session
     */
    @PostMapping("/session")
    public ResponseEntity<ChatSession> createSession(
            @RequestParam Long userId,
            @RequestParam(required = false) String topic) {
        log.info("Create session request received for userId={} hasTopic={}", userId, topic != null && !topic.isBlank());
        try {
            ChatSession session = chatService.createSession(userId, topic);
            log.info("Create session succeeded for userId={} sessionId={}", userId, session.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(session);
        } catch (IllegalArgumentException e) {
            log.warn("Create session rejected for userId={} reason={}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Create session failed unexpectedly for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Send a message and get AI response
     */
    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponseDTO> sendMessage(
            @RequestBody ChatMessageRequestDTO requestDTO) {
        log.info(
                "Chat message request received sessionId={} language={} messageLength={}",
                requestDTO.getSessionId(),
                requestDTO.getLanguage(),
                LogSanitizer.safeLength(requestDTO.getUserMessage()));
        try {
            ChatMessageResponseDTO response = chatService.sendMessage(requestDTO);
            Long assistantMessageId = response.getAssistantMessage() == null ? null : response.getAssistantMessage().getId();
                log.debug(
                    "Chat message processed sessionId={} assistantMessageId={}",
                    response.getSessionId(),
                    assistantMessageId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Chat message rejected sessionId={} reason={}", requestDTO.getSessionId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Chat message failed unexpectedly sessionId={}", requestDTO.getSessionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get chat history for a session
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessageDTO>> getChatHistory(@PathVariable Long sessionId) {
        log.debug("Chat history request received sessionId={}", sessionId);
        try {
            List<ChatMessageDTO> history = chatService.getChatHistory(sessionId);
            log.debug("Chat history returned sessionId={} messageCount={}", sessionId, history.size());
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            log.warn("Chat history rejected sessionId={} reason={}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Chat history failed unexpectedly sessionId={}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all sessions for a user
     */
    @GetMapping("/sessions/{userId}")
    public ResponseEntity<List<ChatSession>> getUserSessions(@PathVariable Long userId) {
        log.debug("User sessions request received userId={}", userId);
        try {
            List<ChatSession> sessions = chatService.getUserSessions(userId);
            log.debug("User sessions returned userId={} sessionCount={}", userId, sessions.size());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("User sessions failed for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
