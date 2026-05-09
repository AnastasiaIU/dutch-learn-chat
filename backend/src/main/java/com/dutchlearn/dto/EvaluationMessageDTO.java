package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationMessageDTO {
    private Long id;
    private Long sessionId;
    private String createdAt;
    private String content;
    private String model;
    private String modelTag;
    private String promptVersion;
    private String responseSource;
    private String languageLevel;
    private CefrEvaluationDTO cefrEvaluation;
}
