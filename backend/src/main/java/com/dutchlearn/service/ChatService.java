package com.dutchlearn.service;

import com.dutchlearn.dto.ChatMessageDTO;
import com.dutchlearn.dto.ChatMessageRequestDTO;
import com.dutchlearn.dto.ChatMessageResponseDTO;
import com.dutchlearn.entity.ChatMessage;
import com.dutchlearn.entity.ChatSession;
import com.dutchlearn.entity.User;
import com.dutchlearn.repository.ChatMessageRepository;
import com.dutchlearn.repository.ChatSessionRepository;
import com.dutchlearn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatService
 * Service for chat message handling
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Create a new chat session
     */
    public ChatSession createSession(Long userId, String topic) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChatSession session = ChatSession.builder()
                .user(user)
                .topic(topic)
                .active(true)
                .build();

        return chatSessionRepository.save(session);
    }

    /**
     * Send a message and get AI response
     */
    public ChatMessageResponseDTO sendMessage(ChatMessageRequestDTO requestDTO) {
        // Get session
        ChatSession session = chatSessionRepository.findById(requestDTO.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        // Save user message
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(ChatMessage.MessageRole.USER)
                .content(requestDTO.getUserMessage())
                .languageUsed(requestDTO.getLanguage())
                .build();
        userMessage = chatMessageRepository.save(userMessage);

        // Get AI response (mock for now)
        String aiResponseContent = generateAiResponse(requestDTO.getUserMessage(), session.getUser().getLanguageLevel());

        // Save AI response
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(aiResponseContent)
                .languageUsed("nl")
                .build();
        assistantMessage = chatMessageRepository.save(assistantMessage);

        // Build response
        return ChatMessageResponseDTO.builder()
                .sessionId(session.getId())
                .userMessage(mapToDTO(userMessage))
                .assistantMessage(mapToDTO(assistantMessage))
                .build();
    }

    /**
     * Get chat history for a session
     */
    public List<ChatMessageDTO> getChatHistory(Long sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return messages.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all sessions for a user
     */
    public List<ChatSession> getUserSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Generate AI response (temporary implementation)
     * TODO: Integrate with actual LLM API (OpenAI, Anthropic)
     */
    private String generateAiResponse(String userMessage, String languageLevel) {
        // This is a placeholder implementation
        // In production, this would call the actual LLM API

        return "Dit is een test antwoord. Je hebt geschreven: '" + userMessage + "'. " +
                "(Opmerking: Dit is een prototype. AI integratie volgt.)";
    }

    /**
     * Map ChatMessage to DTO
     */
    private ChatMessageDTO mapToDTO(ChatMessage message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .role(message.getRole().toString())
                .content(message.getContent())
                .languageUsed(message.getLanguageUsed())
                .createdAt(message.getCreatedAt().format(dateFormatter))
                .build();
    }
}
