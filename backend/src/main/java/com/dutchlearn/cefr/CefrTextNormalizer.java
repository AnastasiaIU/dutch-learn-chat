package com.dutchlearn.cefr;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CefrTextNormalizer {
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}+");
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("[.!?]+");

    public List<String> tokenizeWords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        Matcher matcher = WORD_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    public List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] parts = SENTENCE_SPLIT_PATTERN.split(text);
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    public String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        return word.trim().toLowerCase(Locale.ROOT);
    }
}
