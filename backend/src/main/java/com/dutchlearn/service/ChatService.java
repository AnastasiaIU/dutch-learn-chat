package com.dutchlearn.service;

import com.dutchlearn.cefr.CefrEvaluationResult;
import com.dutchlearn.cefr.CefrResponseValidator;
import com.dutchlearn.dto.CefrEvaluationDTO;
import com.dutchlearn.dto.ChatMessageDTO;
import com.dutchlearn.dto.ChatMessageRequestDTO;
import com.dutchlearn.dto.ChatMessageResponseDTO;
import com.dutchlearn.entity.ChatMessage;
import com.dutchlearn.entity.ChatSession;
import com.dutchlearn.entity.User;
import com.dutchlearn.logging.LogSanitizer;
import com.dutchlearn.repository.ChatMessageRepository;
import com.dutchlearn.repository.ChatSessionRepository;
import com.dutchlearn.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatService
 * Service for chat message handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
        private final CefrResponseValidator cefrResponseValidator;
        private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Create a new chat session
     */
    public ChatSession createSession(Long userId, String topic) {
        log.debug("Creating session for userId={} hasTopic={}", userId, topic != null && !topic.isBlank());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChatSession session = ChatSession.builder()
                .user(user)
                .topic(topic)
                .active(true)
                .build();

        ChatSession saved = chatSessionRepository.save(session);
        log.info("Session created sessionId={} userId={}", saved.getId(), userId);
        return saved;
    }

    /**
     * Update session topic and return a local assistant acknowledgment message.
     */
    @Transactional
    public ChatMessageResponseDTO updateSessionTopic(Long sessionId, String newTopic) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        session.setTopic(newTopic);
        session = chatSessionRepository.save(session);
        
        String cleanTopic = (newTopic == null || newTopic.isBlank()) ? "dagelijkse situaties in Nederland" : newTopic.trim();
        String content = "We praten nu over: " + cleanTopic + ". Wat wil je daarover vertellen?";
        
        // Save local AI acknowledgment
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(content)
                .languageUsed("nl")
                .metadata("{\"responseSource\":\"local\"}")
                .build();
        assistantMessage = chatMessageRepository.save(assistantMessage);

        return ChatMessageResponseDTO.builder()
                .sessionId(session.getId())
                .assistantMessage(mapToDTO(assistantMessage))
                .build();
    }

    /**
     * Send a message and get AI response
     */
        @Transactional
    public ChatMessageResponseDTO sendMessage(ChatMessageRequestDTO requestDTO) {
        log.debug(
                "Processing chat message sessionId={} language={} userMessageLength={}",
                requestDTO.getSessionId(),
                requestDTO.getLanguage(),
                LogSanitizer.safeLength(requestDTO.getUserMessage()));

        // Get session
        ChatSession session = chatSessionRepository.findById(requestDTO.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        // Use recent history as conversational context for model quality.
        List<ChatMessage> recentMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        // Save user message
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(ChatMessage.MessageRole.USER)
                .content(requestDTO.getUserMessage())
                .languageUsed(requestDTO.getLanguage())
                .build();
        userMessage = chatMessageRepository.save(userMessage);

        // Generate AI response with guardrails, RAG, and provider metadata.
        AiGenerationResult aiResult = aiService.generateResponse(
                requestDTO.getUserMessage(),
                session.getUser().getLanguageLevel(),
                session.getTopic(),
                recentMessages);

        String contentToEvaluate = aiResult.getContent();
        // Remove the "difficult/hard words" section from the evaluation content
        String[] markers = {
            "📚 Moeilijke woorden:", 
            "Moeilijke woorden:", 
            "📚 Difficult words:", 
            "Difficult words:",
            "Hard words:",
            "📚"
        };
        for (String marker : markers) {
            int idx = contentToEvaluate.indexOf(marker);
            if (idx != -1) {
                contentToEvaluate = contentToEvaluate.substring(0, idx).trim();
                break;
            }
        }

        CefrEvaluationResult evaluation = cefrResponseValidator.evaluate(
                contentToEvaluate,
                session.getUser().getLanguageLevel());

        if (aiResult.isFallbackUsed()) {
            log.warn(
                    "AI fallback used sessionId={} model={} latencyMs={}",
                    session.getId(),
                    aiResult.getModel(),
                    aiResult.getLatencyMs());
        } else {
                        log.debug(
                    "AI response generated sessionId={} model={} latencyMs={}",
                    session.getId(),
                    aiResult.getModel(),
                    aiResult.getLatencyMs());
        }

        String mergedMetadata = mergeMetadata(aiResult.getMetadataJson(), evaluation);

        // Save AI response
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role(ChatMessage.MessageRole.ASSISTANT)
                .content(aiResult.getContent())
                .languageUsed("nl")
                .metadata(mergedMetadata)
                .build();
        assistantMessage = chatMessageRepository.save(assistantMessage);

        // Build response
        ChatMessageResponseDTO response = ChatMessageResponseDTO.builder()
                .sessionId(session.getId())
                .userMessage(mapToDTO(userMessage))
                .assistantMessage(mapToDTO(assistantMessage))
                .assistantEvaluation(mapToEvaluationDTO(evaluation))
                .build();

        log.debug(
                "Chat message persisted sessionId={} userMessageId={} assistantMessageId={}",
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId());
        return response;
    }

    /**
     * Get chat history for a session
     */
    public List<ChatMessageDTO> getChatHistory(Long sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        log.debug("Loaded chat history sessionId={} messageCount={}", sessionId, messages.size());
        return messages.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all sessions for a user
     */
    public List<ChatSession> getUserSessions(Long userId) {
                List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
                log.debug("Loaded sessions for userId={} sessionCount={}", userId, sessions.size());
                return sessions;
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

        private CefrEvaluationDTO mapToEvaluationDTO(CefrEvaluationResult evaluation) {
                if (evaluation == null) {
                        return null;
                }

                return CefrEvaluationDTO.builder()
                                .targetLevel(evaluation.getTargetLevel())
                                .dataAvailable(evaluation.isDataAvailable())
                                .vocabularySize(evaluation.getVocabularySize())
                                .vocabularyCoverage(evaluation.getVocabularyCoverage())
                                .totalWordCount(evaluation.getTotalWordCount())
                                .unknownWordCount(evaluation.getUnknownWordCount())
                                .unknownWords(evaluation.getUnknownWords())
                                .sentenceCount(evaluation.getSentenceCount())
                                .averageSentenceLength(evaluation.getAverageSentenceLength())
                                .maxSentenceLength(evaluation.getMaxSentenceLength())
                                .violations(evaluation.getViolations())
                                .build();
        }

        private String mergeMetadata(String metadataJson, CefrEvaluationResult evaluation) {
                ObjectNode root = objectMapper.createObjectNode();
                if (metadataJson != null && !metadataJson.isBlank()) {
                        try {
                                JsonNode node = objectMapper.readTree(metadataJson);
                                if (node.isObject()) {
                                        root.setAll((ObjectNode) node);
                                }
                        } catch (Exception ex) {
                                root.put("metadataParseError", "cefr-metadata-merge-failed");
                        }
                }

                if (evaluation != null) {
                        root.set("cefrEvaluation", objectMapper.valueToTree(evaluation));
                }

                try {
                        return objectMapper.writeValueAsString(root);
                } catch (Exception ex) {
                        return metadataJson == null ? "" : metadataJson;
                }
        }
}
