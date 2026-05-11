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
 * OllamaAiProvider
 * Implements the AiProvider interface for Ollama (local model deployment).
 * Communicates with locally deployed models like llama3.1:8b-instruct via Ollama API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaAiProvider implements AiProvider {

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int MAX_HISTORY_MESSAGE_CHARS = 500;

    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.host:http://localhost:11434}")
    private String ollamaHost;

    @Value("${ai.model:llama3.1:8b-instruct}")
    private String selectedModel;

    @Value("${ai.temperature:0.4}")
    private double temperature;

    @Value("${ai.max-tokens:320}")
    private int maxTokens;

    @Value("${ai.request-timeout-seconds:45}")
    private int requestTimeoutSeconds;

    /**
     * Ollama compatibility: minimum and maximum tokens to request
     * Ensures the model has enough context to produce meaningful responses
     */
    private static final int OLLAMA_MIN_PREDICT_TOKENS = 100;
    private static final int OLLAMA_MAX_PREDICT_TOKENS = 1024;

    @Override
    public void validateConfiguration() throws IllegalStateException {
        if (ollamaHost == null || ollamaHost.isBlank()) {
            throw new IllegalStateException(
                "Ollama host is not configured. Set ai.ollama.host in application.yml");
        }

        // Try to check if Ollama is running by calling a health endpoint
        try {
            URI healthUri = URI.create(ollamaHost + "/api/tags");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(healthUri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                    "Ollama is not responding at " + ollamaHost + ". Status: " + response.statusCode());
            }

            log.info("Ollama provider validation successful: {} is running", ollamaHost);
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Failed to validate Ollama connection at " + ollamaHost + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public String callModel(String systemPrompt, String userMessage, List<ChatMessage> recentMessages) throws Exception {
        log.debug("Ollama provider: Calling model={} at host={} timeoutSeconds={}", 
                selectedModel, ollamaHost, requestTimeoutSeconds);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", selectedModel);
        payload.put("stream", false); // CRITICAL: Disable streaming to get complete response
        payload.put("temperature", temperature);
        // Ensure we request enough tokens for meaningful responses (min 100, max 1024)
        int predictTokens = Math.max(OLLAMA_MIN_PREDICT_TOKENS, Math.min(maxTokens, OLLAMA_MAX_PREDICT_TOKENS));
        payload.put("num_predict", predictTokens); // Ollama uses num_predict instead of max_tokens
        log.info("Ollama configuration: model={} temperature={} num_predict={} stream=false", 
                selectedModel, temperature, predictTokens);

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

        log.debug("Ollama request payload: {}", payload.toString());

        // Ollama chat endpoint
        URI ollamaChatUri = URI.create(ollamaHost + "/api/chat");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(ollamaChatUri)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
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
            String detail = responseBodyOneLine.isBlank() ? "" : " body=" + truncate(responseBodyOneLine, 240);
            throw new IllegalStateException(
                "Ollama API returned status " + response.statusCode() + detail);
        }

        log.debug("Ollama raw response: {}", response.body());

        // Ollama returns a single JSON object with the message, not an array
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("message").path("content").asText("").trim();

        log.debug("Ollama extracted content (length={}): {}", content.length(), truncate(content, 200));

        if (content.isBlank()) {
            throw new IllegalStateException("Ollama model returned empty content");
        }

        return content;
    }

    @Override
    public String getProviderName() {
        return "ollama";
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
