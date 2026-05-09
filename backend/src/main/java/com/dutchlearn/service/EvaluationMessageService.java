package com.dutchlearn.service;

import com.dutchlearn.dto.CefrEvaluationDTO;
import com.dutchlearn.dto.EvaluationMessageDTO;
import com.dutchlearn.entity.ChatMessage;
import com.dutchlearn.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationMessageService {
    private static final int MAX_LIMIT = 200;

    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional(readOnly = true)
    public List<EvaluationMessageDTO> getAssistantMessages(int limit, String modelTag) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<ChatMessage> messages = chatMessageRepository
                .findByRoleOrderByCreatedAtDesc(ChatMessage.MessageRole.ASSISTANT, PageRequest.of(0, safeLimit))
                .getContent();

        List<EvaluationMessageDTO> results = new ArrayList<>();
        for (ChatMessage message : messages) {
            EvaluationMessageDTO dto = toDto(message);
            if (dto == null) {
                continue;
            }

            if (modelTag != null && !modelTag.isBlank()) {
                if (dto.getModelTag() == null || !dto.getModelTag().equalsIgnoreCase(modelTag.trim())) {
                    continue;
                }
            }

            results.add(dto);
        }

        log.debug("Loaded evaluation messages count={} filterModelTag={}", results.size(), modelTag);
        return results;
    }

    private EvaluationMessageDTO toDto(ChatMessage message) {
        if (message == null) {
            return null;
        }

        JsonNode metadata = null;
        if (message.getMetadata() != null && !message.getMetadata().isBlank()) {
            try {
                metadata = objectMapper.readTree(message.getMetadata());
            } catch (Exception ex) {
                log.warn("Failed to parse chat metadata messageId={} error={}", message.getId(), ex.getMessage());
            }
        }

        CefrEvaluationDTO cefrEvaluation = null;
        if (metadata != null && metadata.has("cefrEvaluation")) {
            try {
                cefrEvaluation = objectMapper.treeToValue(metadata.get("cefrEvaluation"), CefrEvaluationDTO.class);
            } catch (Exception ex) {
                log.warn("Failed to parse CEFR evaluation messageId={} error={}", message.getId(), ex.getMessage());
            }
        }

        String createdAt = message.getCreatedAt() == null ? "" : message.getCreatedAt().format(dateFormatter);

        return EvaluationMessageDTO.builder()
                .id(message.getId())
                .sessionId(message.getSession() == null ? null : message.getSession().getId())
                .createdAt(createdAt)
                .content(message.getContent())
                .model(readMetadataString(metadata, "model"))
                .modelTag(readMetadataString(metadata, "modelTag"))
                .promptVersion(readMetadataString(metadata, "promptVersion"))
                .responseSource(readMetadataString(metadata, "responseSource"))
                .languageLevel(readMetadataString(metadata, "languageLevel"))
                .cefrEvaluation(cefrEvaluation)
                .build();
    }

    private String readMetadataString(JsonNode metadata, String field) {
        if (metadata == null || field == null) {
            return "";
        }
        return metadata.path(field).asText("");
    }
}
