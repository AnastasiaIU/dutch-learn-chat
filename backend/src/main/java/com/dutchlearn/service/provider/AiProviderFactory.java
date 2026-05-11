package com.dutchlearn.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AiProviderFactory
 * Factory for selecting and instantiating the appropriate AiProvider implementation.
 * Supports switching between providers via configuration (ai.provider property).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiProviderFactory {

    private final GitHubAiProvider gitHubAiProvider;
    private final OllamaAiProvider ollamaAiProvider;

    @Value("${ai.provider:github}")
    private String provider;

    /**
     * Get the configured AiProvider instance.
     *
     * @return The selected AiProvider implementation
     * @throws IllegalArgumentException If the provider is not recognized
     */
    public AiProvider getProvider() {
        return switch (provider.toLowerCase()) {
            case "github" -> {
                log.debug("Using GitHub AI provider");
                yield gitHubAiProvider;
            }
            case "ollama" -> {
                log.debug("Using Ollama AI provider");
                yield ollamaAiProvider;
            }
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider
                    + ". Supported providers: github, ollama");
        };
    }

    /**
     * Get the name of the currently configured provider.
     */
    public String getProviderName() {
        return provider;
    }
}
