package com.dutchlearn.cefr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CefrResponseValidator {
    private final CefrVocabularyService vocabularyService;
    private final CefrTextNormalizer normalizer;

    @Value("${cefr.allowed-levels.a2:A1,A2}")
    private String allowedA2;

    @Value("${cefr.allowed-levels.b1:A1,A2,B1}")
    private String allowedB1;

    @Value("${cefr.constraints.a2.max-sentence-length:12}")
    private int a2MaxSentenceLength;

    @Value("${cefr.constraints.b1.max-sentence-length:15}")
    private int b1MaxSentenceLength;

    @Value("${cefr.constraints.a2.disallowed-tokens:}")
    private String a2DisallowedTokens;

    @Value("${cefr.constraints.b1.disallowed-tokens:}")
    private String b1DisallowedTokens;

    @Value("${cefr.evaluation.max-unknown-words:20}")
    private int maxUnknownWords;

    public CefrEvaluationResult evaluate(String responseText, String targetLevel) {
        CefrLevel level = CefrLevel.fromString(targetLevel).orElse(CefrLevel.A2);

        List<String> tokens = normalizer.tokenizeWords(responseText);
        List<String> sentences = normalizer.splitSentences(responseText);

        Set<CefrLevel> allowedLevels = parseAllowedLevels(level);
        Set<String> allowedWords = vocabularyService.getWordsForLevels(allowedLevels);

        List<String> unknownWords = new ArrayList<>();
        for (String token : tokens) {
            if (!allowedWords.contains(token)) {
                unknownWords.add(token);
            }
        }

        int sentenceCount = sentences.size();
        int totalSentenceWords = 0;
        int maxSentenceLength = 0;
        for (String sentence : sentences) {
            int wordCount = normalizer.tokenizeWords(sentence).size();
            totalSentenceWords += wordCount;
            maxSentenceLength = Math.max(maxSentenceLength, wordCount);
        }

        double averageSentenceLength = sentenceCount == 0
                ? 0.0
                : (double) totalSentenceWords / (double) sentenceCount;

        List<String> violations = new ArrayList<>();

        if (responseText == null || responseText.isBlank()) {
            violations.add("EMPTY_RESPONSE");
        }

        boolean dataAvailable = vocabularyService.isDataAvailable();
        if (!dataAvailable) {
            violations.add("VOCABULARY_DATA_MISSING");
        }

        double vocabularyCoverage = 0.0;
        if (dataAvailable && !tokens.isEmpty()) {
            int knownCount = tokens.size() - unknownWords.size();
            vocabularyCoverage = (double) knownCount / (double) tokens.size();
        }

        int maxAllowedSentenceLength = level == CefrLevel.A2 ? a2MaxSentenceLength : b1MaxSentenceLength;
        if (maxAllowedSentenceLength > 0 && maxSentenceLength > maxAllowedSentenceLength) {
            violations.add("SENTENCE_TOO_LONG");
        }

        Set<String> disallowedTokens = parseDisallowedTokens(level);
        if (!disallowedTokens.isEmpty()) {
            Set<String> found = new HashSet<>();
            for (String token : tokens) {
                if (disallowedTokens.contains(token)) {
                    found.add(token);
                }
            }
            if (!found.isEmpty()) {
                violations.add("DISALLOWED_TOKENS:" + String.join(",", found));
            }
        }

        List<String> unknownPreview = unknownWords.stream()
                .distinct()
                .limit(maxUnknownWords)
                .collect(Collectors.toList());

        return CefrEvaluationResult.builder()
                .targetLevel(level.name())
                .dataAvailable(dataAvailable)
                .vocabularySize(vocabularyService.getTotalWordCount())
                .vocabularyCoverage(vocabularyCoverage)
                .totalWordCount(tokens.size())
                .unknownWordCount(unknownWords.size())
                .unknownWords(unknownPreview)
                .sentenceCount(sentenceCount)
                .averageSentenceLength(averageSentenceLength)
                .maxSentenceLength(maxSentenceLength)
                .violations(violations)
                .build();
    }

    private Set<CefrLevel> parseAllowedLevels(CefrLevel target) {
        String configured = target == CefrLevel.A2 ? allowedA2 : allowedB1;
        Set<CefrLevel> levels = EnumSet.noneOf(CefrLevel.class);
        if (configured == null || configured.isBlank()) {
            return levels;
        }

        String[] parts = configured.split(",");
        for (String part : parts) {
            CefrLevel.fromString(part).ifPresent(levels::add);
        }

        return levels;
    }

    private Set<String> parseDisallowedTokens(CefrLevel target) {
        String configured = target == CefrLevel.A2 ? a2DisallowedTokens : b1DisallowedTokens;
        Set<String> tokens = new HashSet<>();
        if (configured == null || configured.isBlank()) {
            return tokens;
        }

        String[] parts = configured.split(",");
        for (String part : parts) {
            String token = normalizer.normalizeWord(part);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }

        return tokens;
    }
}
