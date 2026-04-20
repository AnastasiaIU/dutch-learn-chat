package com.dutchlearn.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiGenerationResult {
    private String content;
    private String metadataJson;
    private boolean fallbackUsed;
    private long latencyMs;
    private String promptVersion;
    private String model;
}
