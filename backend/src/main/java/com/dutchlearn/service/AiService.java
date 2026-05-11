package com.dutchlearn.service;

import com.dutchlearn.entity.ChatMessage;
import com.dutchlearn.logging.LogSanitizer;
import com.dutchlearn.service.provider.AiProvider;
import com.dutchlearn.service.provider.AiProviderFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AiService
 * Handles model prompting, optional RAG context, and AI API calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

        private static final String DEFAULT_TOPIC = "dagelijkse situaties in Nederland";
        private static final List<String> TOPIC_SUGGESTIONS = List.of(
            "werk",
            "boodschappen",
            "reizen",
            "gezondheid",
            "buren");
        private static final String RESPONSE_SOURCE_MODEL = "model";
        private static final String RESPONSE_SOURCE_FALLBACK = "fallback";
        private static final String RESPONSE_SOURCE_LOCAL = "local";
        private static final List<String> HARMFUL_PHRASES = List.of(
            "self harm",
            "self-harm");
        private static final List<String> HARMFUL_WORDS = List.of(
            "beledig",
            "bedreig",
            "geweld",
            "haat",
            "mishandel",
            "moord",
            "scheld",
            "uitlachen",
            "verkracht",
            "zelfmoord",
            "abuse",
            "assault",
            "harass",
            "hate",
            "insult",
            "kill",
            "murder",
            "rape",
            "suicide",
            "threat",
            "violent",
            "violence");

    private final ObjectMapper objectMapper;
    private final RagService ragService;
    private final AiProviderFactory providerFactory;

    @Value("${ai.language-level:A2}")
    private String defaultLanguageLevel;

    @Value("${ai.prompt.version:v1-a2b1-guardrails}")
    private String promptVersion;

    @Value("${ai.model-tag:baseline}")
    private String modelTag;

    @Value("${ai.model:gpt-4o-mini}")
    private String selectedModel;

    @Value("${ai.max-input-chars:800}")
    private int maxInputChars;

    @Value("${ai.max-history-chars:2400}")
    private int maxHistoryChars;

    @Value("${ai.mock.enabled:false}")
    private boolean mockMode;

    @Value("${ai.rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${ai.rag.max-context-items:3}")
    private int ragMaxContextItems;

    @Value("${ai.rag.max-snippet-chars:220}")
    private int ragMaxSnippetChars;

    /**
     * Generate response from model provider with guardrail prompting.
     */
    public AiGenerationResult generateResponse(
            String userMessage,
            String languageLevel,
            String topic,
            List<ChatMessage> recentMessages) {

        long startedAt = System.currentTimeMillis();
        String effectiveLevel = (languageLevel == null || languageLevel.isBlank())
                ? defaultLanguageLevel
                : languageLevel;

        String normalizedMessage = normalizeUserMessage(userMessage);

        if (shouldSendTopicIntro(normalizedMessage, topic, recentMessages)) {
            return buildTopicSelectionResult(effectiveLevel);
        }

        List<RagService.RagMatch> ragMatches = ragEnabled
            ? ragService.retrieve(normalizedMessage, ragMaxContextItems)
            : List.of();
        String ragContext = ragService.buildContextBlock(ragMatches, ragMaxSnippetChars);

        String providerName = providerFactory.getProviderName();
        log.debug(
            "AI generation started provider={} model={} languageLevel={} historyCount={} ragEnabled={} ragHits={} userMessageLength={}",
            providerName,
            selectedModel,
            effectiveLevel,
            recentMessages == null ? 0 : recentMessages.size(),
            ragEnabled,
            ragMatches.size(),
            LogSanitizer.safeLength(normalizedMessage));

        if (mockMode) {
            log.warn("AI generation using fallback because mock mode is enabled");
            return buildFallbackResult(
                    normalizedMessage,
                    effectiveLevel,
                    topic,
                    ragMatches,
                    System.currentTimeMillis() - startedAt,
                    "Mock mode is enabled.");
        }

        try {
            String prompt = buildSystemPrompt(effectiveLevel, topic, ragContext);
            AiProvider provider = providerFactory.getProvider();
            provider.validateConfiguration();
            String modelResponse = provider.callModel(prompt, normalizedMessage, recentMessages);

            long latencyMs = System.currentTimeMillis() - startedAt;
            log.info("AI generation completed provider={} model={} latencyMs={}", providerName, selectedModel, latencyMs);

            return buildResult(
                    modelResponse,
                    false,
                    "",
                    effectiveLevel,
                    ragMatches,
                    latencyMs,
                    RESPONSE_SOURCE_MODEL);
        } catch (Exception ex) {
            log.warn(
                "AI generation failed provider={} model={} errorType={} errorMessage={}",
                providerName,
                selectedModel,
                ex.getClass().getSimpleName(),
                ex.getMessage());
            return buildFallbackResult(
                    normalizedMessage,
                    effectiveLevel,
                    topic,
                    ragMatches,
                    System.currentTimeMillis() - startedAt,
                    ex.getMessage());
        }
    }



    private String buildSystemPrompt(String languageLevel, String topic, String ragContext) {
        String effectiveTopic = resolveTopic(topic);

        StringBuilder prompt = new StringBuilder();
        // - You are a friendly Dutch conversation partner for adult NT2 learners.
        prompt.append("Je bent een vriendelijke Nederlandse gesprekspartner voor volwassen NT2-leerders.\n")
                // - Goal: always answer in Dutch at CEFR level {level}.
                .append("Doel: antwoord altijd in het Nederlands op CEFR niveau ")
                .append(languageLevel)
                .append(".\n\n")
                // - Mandatory rules:
                .append("Verplichte regels:\n")
                // - 1. Use short, clear sentences (avg 8-12 words).
                .append("1. Gebruik korte, duidelijke zinnen (gemiddeld 8-12 woorden).\n")
                // - 2. Prefer present tense and everyday vocabulary.
                .append("2. Gebruik vooral tegenwoordige tijd en dagelijkse woordenschat.\n")
                // - 3. Stay on topic: {topic}.
                .append("3. Blijf bij onderwerp: ")
                .append(effectiveTopic)
                .append(".\n")
                // - 4. If the user writes English, still respond in Dutch.
                .append("4. Als de gebruiker Engels schrijft, reageer toch in het Nederlands.\n")
                // - 5. Politely refuse harmful, offensive, illegal, or hateful requests in Dutch.
                .append("5. Weiger schadelijke, beledigende, illegale of haatdragende verzoeken beleefd in het Nederlands.\n")
                // - 6. Never say you are human or provide official language certification.
                .append("6. Zeg nooit dat je een mens bent of officiële taalcertificering geeft.\n\n")
                // - Output format:
                .append("Uitvoerformaat:\n")
                // - First 1 short paragraph with the main answer.
                .append("1. Eerst je antwoord: 1 korte alinea met het hoofdantwoord.\n")
                // - Then: a follow-up question that keeps conversation going (not final, not the end)
                .append("\n2. Daarna een vraag: Eindig met een vriendelijke vraag om het gesprek voort te zetten.\n")
                .append("   (Dit is NIET het einde - de gebruiker kan meer vragen stellen!)\n")
                .append("   Voorbeelden: \"En jij, hoe gaat het met jou?\" of \"Wat is jouw ervaring hiermee?\"\n")
                // - Finally: difficult words section (only if useful, max 2 words)
                .append("\n3. Tot slot de woorden: Alleen indien nuttig, exact deze sectie met maximaal 2 woorden:\n")
                .append("📚 Moeilijke woorden:\n")
                // - - word: short explanation in English
                .append("- woord: korte uitleg in het Engels\n")
                // - - word: short explanation in English
                .append("- woord: korte uitleg in het Engels\n");

        if (!ragContext.isBlank()) {
            prompt.append("\nGebruik waar relevant ook deze betrouwbare leercontext:\n")
                    .append(ragContext)
                    .append("\nAls context en algemene kennis botsen, geef voorrang aan de context hierboven.");
        }

        return prompt.toString();
    }

    // Fallback is used when the model cannot be called; it keeps replies safe and short.
    private AiGenerationResult buildFallbackResult(
            String userMessage,
            String languageLevel,
            String topic,
            List<RagService.RagMatch> ragMatches,
            long latencyMs,
            String reason) {
        String safeTopic = resolveTopic(topic);

        String fallbackResponse;
        if (isLikelyHarmfulPrompt(userMessage)) {
            // - Sorry, I cannot help with that.
            fallbackResponse = "Sorry, daar kan ik niet mee helpen. "
                // - Let's practice with a safe topic, for example work, shopping, or travel.
                + "Laten we oefenen met een veilig onderwerp, bijvoorbeeld werk, boodschappen of reizen."
                // - Difficult words: safe, topic.
                + "\n\n📚 Moeilijke woorden:\n"
                + "- veilig: safe\n"
                + "- onderwerp: topic";
        } else {
            // - Thanks for your message. We practice calm Dutch at level {level}.
            fallbackResponse = "Dank je voor je bericht. We oefenen rustig Nederlands op niveau " + languageLevel + ". "
                // - Let's talk about {topic}. Can you tell more?
                + "Laten we praten over " + safeTopic + ". Kun je daar iets meer over vertellen?"
                // - Difficult words: calm, to tell.
                + "\n\n📚 Moeilijke woorden:\n"
                + "- rustig: calm\n"
                + "- vertellen: to tell";
        }

        log.info(
                "AI fallback used reason={} languageLevel={} userMessageLength={}",
                reason,
                languageLevel,
                LogSanitizer.safeLength(userMessage));

        // Keep user message visible for transparency even if the UI hides metadata.
        if (userMessage != null && !userMessage.isBlank()) {
            fallbackResponse = fallbackResponse + "\n\n(Jouw bericht: \"" + truncate(userMessage.trim(), 120) + "\")";
        }

        return buildResult(fallbackResponse, true, reason, languageLevel, ragMatches, latencyMs, RESPONSE_SOURCE_FALLBACK);
    }

    private boolean isLikelyHarmfulPrompt(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String lowered = userMessage.toLowerCase();
        for (String phrase : HARMFUL_PHRASES) {
            if (lowered.contains(phrase)) {
                return true;
            }
        }

        for (String word : HARMFUL_WORDS) {
            if (containsWholeWord(lowered, word)) {
                return true;
            }
        }

        return false;
    }

    private AiGenerationResult buildResult(
            String content,
            boolean fallbackUsed,
            String fallbackReason,
            String languageLevel,
            List<RagService.RagMatch> ragMatches,
            long latencyMs,
            String responseSource) {

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", providerFactory.getProviderName());
        metadata.put("model", selectedModel);
        metadata.put("modelTag", modelTag);
        metadata.put("promptVersion", promptVersion);
        metadata.put("languageLevel", languageLevel);
        metadata.put("fallbackUsed", fallbackUsed);
        metadata.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
        metadata.put("latencyMs", latencyMs);
        metadata.put("responseSource", responseSource);
        metadata.put("ragEnabled", ragEnabled);
        metadata.put("ragUsed", !ragMatches.isEmpty());
        metadata.put("ragSources", ragMatches.stream().map(RagService.RagMatch::getSourceId).toList());

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            metadataJson = "{\"serializationError\":\"metadata unavailable\"}";
        }

        return AiGenerationResult.builder()
                .content(content)
                .metadataJson(metadataJson)
                .fallbackUsed(fallbackUsed)
                .latencyMs(latencyMs)
                .promptVersion(promptVersion)
                .model(selectedModel)
                .build();
    }

    private String normalizeUserMessage(String userMessage) {
        if (userMessage == null) {
            return "";
        }

        String trimmed = userMessage.trim();
        if (maxInputChars > 0 && trimmed.length() > maxInputChars) {
            return truncateForPrompt(trimmed, maxInputChars);
        }

        return trimmed;
    }

    private String resolveTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return DEFAULT_TOPIC;
        }

        return topic.trim();
    }

    private boolean shouldSendTopicIntro(
            String userMessage,
            String topic,
            List<ChatMessage> recentMessages) {
        boolean firstInteraction = recentMessages == null || recentMessages.isEmpty();
        return firstInteraction
                && (topic == null || topic.isBlank())
                && (userMessage == null || userMessage.isBlank());
    }

    private AiGenerationResult buildTopicSelectionResult(String languageLevel) {
        String suggestions = String.join(", ", TOPIC_SUGGESTIONS);
        String content = "Welkom! Kies een onderwerp om te oefenen. "
                + "Bijvoorbeeld: " + suggestions + ". "
                + "Je kunt ook zelf een onderwerp typen.";

        return buildResult(content, false, "", languageLevel, List.of(), 0L, RESPONSE_SOURCE_LOCAL);
    }

    private boolean containsWholeWord(String text, String word) {
        if (word == null || word.isBlank()) {
            return false;
        }

        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }

    private String truncateForPrompt(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
