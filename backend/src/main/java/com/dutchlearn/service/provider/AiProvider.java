package com.dutchlearn.service.provider;

import com.dutchlearn.entity.ChatMessage;

import java.util.List;

/**
 * AiProvider
 * Abstraction for different AI model providers (GitHub, Ollama, etc).
 * Implementations handle provider-specific API calls and response parsing.
 */
public interface AiProvider {
    /**
     * Send a request to the AI model and return the response.
     *
     * @param systemPrompt The system prompt that guides model behavior
     * @param userMessage The user's current message
     * @param recentMessages Chat history for context
     * @return The model's response content
     * @throws Exception If the API call fails
     */
    String callModel(String systemPrompt, String userMessage, List<ChatMessage> recentMessages) throws Exception;

    /**
     * Validate that the provider is properly configured.
     *
     * @throws IllegalStateException If required configuration is missing
     */
    void validateConfiguration() throws IllegalStateException;

    /**
     * Get the display name of this provider.
     */
    String getProviderName();
}
