package com.dutchlearn.cefr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CefrVocabularyService {
    private static final Pattern LEVEL_COLUMN_PATTERN = Pattern.compile("(?i)(?:@)?(A1|A2|B1|B2|C1|C2)");
    private static final List<String> WORD_COLUMN_CANDIDATES = List.of("word", "lemma", "lexeme");
    // Fallback for TSVs that provide a single CEFR column (NT2Lex uses per-level columns like D@A1/F@A1).
    private static final List<String> LEVEL_COLUMN_CANDIDATES = List.of("level", "cefr", "cefr_level");

    private final ObjectMapper objectMapper;
    private final CefrTextNormalizer normalizer;

    @Value("${cefr.vocabulary.path:cefr/dutch-vocabulary.json}")
    private String vocabularyPath;

    @Value("${cefr.vocabulary.format:auto}")
    private String vocabularyFormat;

    @Value("${cefr.vocabulary.min-entries:200}")
    private int minEntries;

    private final Map<CefrLevel, Set<String>> wordsByLevel = new EnumMap<>(CefrLevel.class);
    private boolean dataAvailable = false;
    private int totalWordCount = 0;

    @PostConstruct
    public void loadVocabulary() {
        for (CefrLevel level : CefrLevel.values()) {
            wordsByLevel.put(level, new HashSet<>());
        }

        Resource resource = new ClassPathResource(vocabularyPath);
        if (!resource.exists()) {
            log.warn("CEFR vocabulary file not found at classpath:{}", vocabularyPath);
            return;
        }

        try (InputStream input = resource.getInputStream()) {
            byte[] content = input.readAllBytes();
            if (content.length == 0) {
                log.warn("CEFR vocabulary file is empty at {}", vocabularyPath);
                return;
            }

            boolean loaded = loadByFormat(content);
            if (!loaded) {
                log.warn("CEFR vocabulary could not be parsed at {}", vocabularyPath);
                return;
            }

            totalWordCount = wordsByLevel.values().stream().mapToInt(Set::size).sum();
            for (CefrLevel level : CefrLevel.values()) {
                log.info("Level {} has {} words", level, wordsByLevel.get(level).size());
            }
            dataAvailable = totalWordCount > 0;

            if (!dataAvailable) {
                log.warn("CEFR vocabulary file loaded but no valid entries were found at {}", vocabularyPath);
            } else if (totalWordCount < minEntries) {
                log.warn("CEFR vocabulary size={} below configured minimum={}", totalWordCount, minEntries);
            } else {
                log.info("CEFR vocabulary loaded entries={}", totalWordCount);
            }
        } catch (Exception ex) {
            log.warn("Failed to load CEFR vocabulary from {} error={}", vocabularyPath, ex.getMessage());
        }
    }

    public boolean isDataAvailable() {
        return dataAvailable;
    }

    public int getTotalWordCount() {
        return totalWordCount;
    }

    public Set<String> getWordsForLevels(Set<CefrLevel> levels) {
        Set<String> combined = new HashSet<>();
        for (CefrLevel level : levels) {
            Set<String> words = wordsByLevel.get(level);
            if (words != null) {
                combined.addAll(words);
            }
        }
        return combined;
    }

    private boolean loadByFormat(byte[] content) {
        String format = vocabularyFormat == null ? "auto" : vocabularyFormat.trim().toLowerCase(Locale.ROOT);
        if (format.isBlank() || "auto".equals(format)) {
            String pathLower = vocabularyPath == null ? "" : vocabularyPath.toLowerCase(Locale.ROOT);
            if (pathLower.endsWith(".json")) {
                return loadFromJson(content);
            }
            if (pathLower.endsWith(".tsv") || pathLower.endsWith(".txt")) {
                return loadFromTsv(content);
            }

            if (loadFromJson(content)) {
                return true;
            }
            return loadFromTsv(content);
        }

        if ("json".equals(format)) {
            return loadFromJson(content);
        }

        if ("tsv".equals(format)) {
            return loadFromTsv(content);
        }

        log.warn("Unknown CEFR vocabulary format '{}'", format);
        return false;
    }

    private boolean loadFromJson(byte[] content) {
        try (InputStream input = new ByteArrayInputStream(content)) {
            List<CefrVocabularyEntry> entries = objectMapper.readValue(input, new TypeReference<>() {});
            for (CefrVocabularyEntry entry : entries) {
                if (entry == null || entry.getWord() == null || entry.getWord().isBlank()) {
                    continue;
                }

                CefrLevel.fromString(entry.getLevel())
                        .ifPresent(level -> wordsByLevel.get(level)
                                .add(normalizer.normalizeWord(entry.getWord())));
            }
            return true;
        } catch (Exception ex) {
            log.warn("CEFR JSON parsing failed error={}", ex.getMessage());
            return false;
        }
    }

    private boolean loadFromTsv(byte[] content) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        } catch (Exception ex) {
            log.warn("CEFR TSV read failed error={}", ex.getMessage());
            return false;
        }

        if (lines.isEmpty()) {
            return false;
        }

        List<String> header = splitTsvLine(lines.get(0));
        int wordIndex = findColumnIndex(header, WORD_COLUMN_CANDIDATES);
        if (wordIndex < 0) {
            log.warn("CEFR TSV missing word/lemma column in header");
            return false;
        }

        int levelIndex = findColumnIndex(header, LEVEL_COLUMN_CANDIDATES);
        // NT2Lex encodes CEFR presence in per-level columns (e.g., D@A1/F@A1) rather than a single level field.
        Map<CefrLevel, List<Integer>> levelColumns = levelIndex < 0
                ? findLevelColumns(header, wordIndex)
                : Map.of();

        if (levelIndex < 0 && levelColumns.isEmpty()) {
            log.warn("CEFR TSV missing CEFR level columns");
            return false;
        }

        for (int i = 1; i < lines.size(); i++) {
            List<String> columns = splitTsvLine(lines.get(i));
            if (columns.size() <= wordIndex) {
                continue;
            }

            String word = normalizer.normalizeWord(columns.get(wordIndex));
            if (word.isBlank()) {
                continue;
            }

            CefrLevel assigned = null;
            if (levelIndex >= 0 && columns.size() > levelIndex) {
                assigned = CefrLevel.fromString(columns.get(levelIndex)).orElse(null);
            } else {
                Set<CefrLevel> presentLevels = new HashSet<>();
                for (Map.Entry<CefrLevel, List<Integer>> entry : levelColumns.entrySet()) {
                    for (Integer columnIndex : entry.getValue()) {
                        if (columnIndex < columns.size()) {
                            String value = columns.get(columnIndex).trim();
                            if (hasValue(value)) {
                                presentLevels.add(entry.getKey());
                                break;
                            }
                        }
                    }
                }

                assigned = presentLevels.stream()
                        .min(Comparator.comparingInt(CefrLevel::getRank))
                        .orElse(null);
            }

            if (assigned != null) {
                wordsByLevel.get(assigned).add(word);
            }
        }

        return true;
    }

    private List<String> splitTsvLine(String line) {
        String[] parts = line.split("\t", -1);
        List<String> columns = new ArrayList<>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == 0 && part.startsWith("\uFEFF")) {
                part = part.substring(1);
            }
            columns.add(part.trim());
        }
        return columns;
    }

    private int findColumnIndex(List<String> header, List<String> candidates) {
        for (int i = 0; i < header.size(); i++) {
            String value = header.get(i).toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (value.equals(candidate)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Map<CefrLevel, List<Integer>> findLevelColumns(List<String> header, int wordIndex) {
        Map<CefrLevel, List<Integer>> columns = new EnumMap<>(CefrLevel.class);
        for (CefrLevel level : CefrLevel.values()) {
            columns.put(level, new ArrayList<>());
        }

        for (int i = 0; i < header.size(); i++) {
            if (i == wordIndex) {
                continue;
            }
            String value = header.get(i);
            Matcher matcher = LEVEL_COLUMN_PATTERN.matcher(value);
            if (matcher.find()) {
                int columnIndex = i;
                CefrLevel.fromString(matcher.group(1))
                        .ifPresent(level -> columns.get(level).add(columnIndex));
            }
        }

        columns.values().removeIf(List::isEmpty);
        return columns;
    }

    private boolean hasValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || "-".equals(trimmed)) {
            return false;
        }
        return !"0".equals(trimmed) && !"0.0".equals(trimmed);
    }
}
