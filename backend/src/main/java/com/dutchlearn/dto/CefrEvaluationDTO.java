package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CefrEvaluationDTO {
    private String targetLevel;
    private boolean dataAvailable;
    private int vocabularySize;
    private double vocabularyCoverage;
    private int totalWordCount;
    private int unknownWordCount;
    private List<String> unknownWords;
    private int sentenceCount;
    private double averageSentenceLength;
    private int maxSentenceLength;
    private List<String> violations;
}
