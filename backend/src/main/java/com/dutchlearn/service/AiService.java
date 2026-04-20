package com.dutchlearn.service;

import com.dutchlearn.entity.ChatMessage;
import com.dutchlearn.logging.LogSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AiService
 * Handles model prompting, optional RAG context, and OpenAI API calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private static final String GITHUB_MODELS_URL = "https://models.inference.ai.azure.com/chat/completions";
    private static final int MAX_HISTORY_MESSAGES = 6;

    private final ObjectMapper objectMapper;
    private final RagService ragService;

    @Value("${ai.language-level:A2-B1}")
    private String defaultLanguageLevel;

    @Value("${ai.provider:github}")
    private String provider;

    @Value("${ai.prompt.version:v1-a2b1-guardrails}")
    private String promptVersion;

    // Use GitHub token as API key (which gives access to models for free for students)
    @Value("${ai.api-key:}")
    private String apiKey;

    // e.g. gpt-4o, Anthropic-Claude-3.5-Sonnet, google-gemini-1.5-pro
    @Value("${ai.model:gpt-4o-mini}")
    private String selectedModel;

    @Value("${ai.temperature:0.4}")
    private double temperature;

    @Value("${ai.max-tokens:320}")
    private int maxTokens;

    @Value("${ai.request-timeout-seconds:45}")
    private int requestTimeoutSeconds;

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

        List<RagService.RagMatch> ragMatches = ragEnabled
                ? ragService.retrieve(userMessage, ragMaxContextItems)
                : List.of();
        String ragContext = ragService.buildContextBlock(ragMatches, ragMaxSnippetChars);

        log.debug(
            "AI generation started provider={} model={} languageLevel={} historyCount={} ragEnabled={} ragHits={} userMessageLength={}",
            provider,
            selectedModel,
            effectiveLevel,
            recentMessages == null ? 0 : recentMessages.size(),
            ragEnabled,
            ragMatches.size(),
            LogSanitizer.safeLength(userMessage));

        if (mockMode || apiKey == null || apiKey.isBlank()) {
            log.warn(
                "AI generation using fallback because API key is missing or mock mode is enabled (expected GITHUB_TOKEN)");
            return buildFallbackResult(
                    userMessage,
                    effectiveLevel,
                    topic,
                    ragMatches,
                    System.currentTimeMillis() - startedAt,
                    "API key missing or mock mode enabled. Set GITHUB_TOKEN.");
        }

        try {
            String prompt = buildSystemPrompt(effectiveLevel, topic, ragContext);
            String modelResponse = callUniversalApi(prompt, userMessage, recentMessages);

            long latencyMs = System.currentTimeMillis() - startedAt;
            log.info("AI generation completed provider={} model={} latencyMs={}", provider, selectedModel, latencyMs);

            return buildResult(
                    modelResponse,
                    false,
                    "",
                    effectiveLevel,
                    ragMatches,
                latencyMs);
        } catch (Exception ex) {
            log.warn(
                "AI generation failed provider={} model={} errorType={} errorMessage={}",
                provider,
                selectedModel,
                ex.getClass().getSimpleName(),
                ex.getMessage());
            return buildFallbackResult(
                    userMessage,
                    effectiveLevel,
                    topic,
                    ragMatches,
                    System.currentTimeMillis() - startedAt,
                    ex.getMessage());
        }
    }

    private String callUniversalApi(String systemPrompt, String userMessage, List<ChatMessage> recentMessages) throws Exception {
        log.debug("Calling provider API model={} timeoutSeconds={}", selectedModel, requestTimeoutSeconds);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", selectedModel); // Sends gpt/claude/gemini interchangeably 
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);

        ArrayNode messages = payload.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", systemPrompt);

        for (ChatMessage message : limitHistory(recentMessages)) {
            String role = message.getRole() == ChatMessage.MessageRole.USER ? "user" : "assistant";
            messages.addObject()
                    .put("role", role)
                    .put("content", truncate(message.getContent(), 500));
        }

        messages.addObject()
                .put("role", "user")
                .put("content", userMessage == null ? "" : userMessage.trim());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_MODELS_URL))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() >= 400) {
            String responseBody = response.body() == null ? "" : response.body();
            String responseBodyOneLine = responseBody.replace("\r", " ").replace("\n", " ").trim();
            String loweredBody = responseBodyOneLine.toLowerCase();

            if (response.statusCode() == 401
                    && loweredBody.contains("models")
                    && loweredBody.contains("permission is required")) {
                throw new IllegalStateException(
                    "API returned status 401: token is missing GitHub Models permission. "
                        + "Create a token with Models access and set GITHUB_TOKEN.");
            }

            String detail = responseBodyOneLine.isBlank() ? "" : " body=" + truncate(responseBodyOneLine, 240);
            throw new IllegalStateException("API returned status " + response.statusCode() + detail);
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();

        if (content.isBlank()) {
            throw new IllegalStateException("Model returned empty content");
        }

        return content;
    }

    private String buildSystemPrompt(String languageLevel, String topic, String ragContext) {
        String effectiveTopic = (topic == null || topic.isBlank())
                ? "dagelijkse situaties in Nederland"
                : topic.trim();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Je bent een vriendelijke Nederlandse gesprekspartner voor volwassen NT2-leerders.\n")
                .append("Doel: antwoord altijd in het Nederlands op CEFR niveau ")
                .append(languageLevel)
                .append(".\n\n")
                .append("Verplichte regels:\n")
                .append("1. Gebruik korte, duidelijke zinnen (gemiddeld 8-12 woorden).\n")
                .append("2. Gebruik vooral tegenwoordige tijd en dagelijkse woordenschat.\n")
                .append("3. Blijf bij onderwerp: ")
                .append(effectiveTopic)
                .append(".\n")
                .append("4. Als de gebruiker Engels schrijft, reageer toch in het Nederlands.\n")
                .append("5. Weiger schadelijke, beledigende, illegale of haatdragende verzoeken beleefd in het Nederlands.\n")
                .append("6. Zeg nooit dat je een mens bent of officiële taalcertificering geeft.\n\n")
                .append("Uitvoerformaat:\n")
                .append("- Eerst 1 korte alinea met het hoofdantwoord.\n")
                .append("- Daarna alleen indien nuttig: exact deze sectie met maximaal 2 woorden:\n")
                .append("📚 Moeilijke woorden:\n")
                .append("- woord: korte uitleg in het Engels\n")
                .append("- woord: korte uitleg in het Engels\n");

        if (!ragContext.isBlank()) {
            prompt.append("\nGebruik waar relevant ook deze betrouwbare leercontext:\n")
                    .append(ragContext)
                    .append("\nAls context en algemene kennis botsen, geef voorrang aan de context hierboven.");
        }

        return prompt.toString();
    }

    private List<ChatMessage> limitHistory(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, recentMessages.size() - MAX_HISTORY_MESSAGES);
        return recentMessages.subList(fromIndex, recentMessages.size());
    }

    private AiGenerationResult buildFallbackResult(
            String userMessage,
            String languageLevel,
            String topic,
            List<RagService.RagMatch> ragMatches,
            long latencyMs,
            String reason) {

        String safeTopic = (topic == null || topic.isBlank()) ? "dagelijkse situaties" : topic;

        String fallbackResponse;
        if (isLikelyHarmfulPrompt(userMessage)) {
            fallbackResponse = "Sorry, daar kan ik niet mee helpen. "
                + "Laten we oefenen met een veilig onderwerp, bijvoorbeeld werk, boodschappen of reizen."
                + "\n\n📚 Moeilijke woorden:\n"
                + "- veilig: safe\n"
                + "- onderwerp: topic";
        } else {
            fallbackResponse = "Dank je voor je bericht. We oefenen rustig Nederlands op niveau " + languageLevel + ". "
                + "Laten we praten over " + safeTopic + ". Kun je daar iets meer over vertellen?"
                + "\n\n📚 Moeilijke woorden:\n"
                + "- rustig: calm\n"
                + "- vertellen: to tell";
        }

        // Keep user message visible for transparency when fallback is active.
        if (userMessage != null && !userMessage.isBlank()) {
            fallbackResponse = fallbackResponse + "\n\n(Jouw bericht: \"" + truncate(userMessage.trim(), 120) + "\")";
        }

        return buildResult(fallbackResponse, true, reason, languageLevel, ragMatches, latencyMs);
    }

    private boolean isLikelyHarmfulPrompt(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String lowered = userMessage.toLowerCase();
        return lowered.contains("beledig")
                || lowered.contains("haat")
                || lowered.contains("uitlachen")
                || lowered.contains("scheld")
                || lowered.contains("insult")
                || lowered.contains("hurt")
                || lowered.contains("harm");
    }

    private AiGenerationResult buildResult(
            String content,
            boolean fallbackUsed,
            String fallbackReason,
            String languageLevel,
            List<RagService.RagMatch> ragMatches,
            long latencyMs) {

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider);
        metadata.put("model", selectedModel);
        metadata.put("promptVersion", promptVersion);
        metadata.put("languageLevel", languageLevel);
        metadata.put("fallbackUsed", fallbackUsed);
        metadata.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
        metadata.put("latencyMs", latencyMs);
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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }
}
