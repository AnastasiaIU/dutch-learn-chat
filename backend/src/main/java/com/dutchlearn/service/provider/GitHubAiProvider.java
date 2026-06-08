package com.dutchlearn.service.provider;

import com.dutchlearn.entity.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * GitHubAiProvider
 * Implements the AiProvider interface for GitHub Models API.
 * Handles authentication and API communication with GitHub's managed model endpoints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubAiProvider implements AiProvider {

    private static final String GITHUB_MODELS_URL = "https://models.inference.ai.azure.com/chat/completions";
    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int MAX_HISTORY_MESSAGE_CHARS = 500;

    private final ObjectMapper objectMapper;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:gpt-4o-mini}")
    private String selectedModel;

    @Value("${ai.temperature:0.4}")
    private double temperature;

    @Value("${ai.max-tokens:320}")
    private int maxTokens;

    @Value("${ai.request-timeout-seconds:360}")
    private int requestTimeoutSeconds;

    @Override
    public void validateConfiguration() throws IllegalStateException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "GitHub API key is missing. Set GITHUB_TOKEN environment variable with Models permission.");
        }
    }

    @Override
    public String callModel(String systemPrompt, String userMessage, List<ChatMessage> recentMessages) throws Exception {
        log.debug("GitHub provider: Calling model={} timeoutSeconds={}", selectedModel, requestTimeoutSeconds);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", selectedModel);
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
                    .put("content", truncate(message.getContent(), MAX_HISTORY_MESSAGE_CHARS));
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

    @Override
    public String getProviderName() {
        return "github";
    }

    private List<ChatMessage> limitHistory(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, recentMessages.size() - MAX_HISTORY_MESSAGES);
        List<ChatMessage> latestMessages = recentMessages.subList(fromIndex, recentMessages.size());
        return latestMessages;
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
