package com.dutchlearn.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RagService - Retrieval-Augmented Generation
 * This service provides context (e.g. Dutch grammar rules or vocabulary)
 * based on user input, to guide the AI towards better learning outcomes.
 */
@Service
@Slf4j
public class RagService {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagMatch {
        private String sourceId;
        private String text;
    }

    /**
     * Simple keyword-based RAG simulation.
     * In a production app, this would query a Vector Database (like PGVector or Pinecone).
     */
    public List<RagMatch> retrieve(String query, int maxItems) {
        if (query == null || query.isBlank()) {
            log.debug("RAG retrieve skipped because query is empty");
            return List.of();
        }

        List<RagMatch> matches = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.contains("because") || lowerQuery.contains("omdat")) {
            matches.add(new RagMatch("Grammar_Conjunction", "Rule: In Dutch, subordinate clauses starting with 'omdat' (because) push the verb to the end of the sentence."));
        }
        if (lowerQuery.contains("tomorrow") || lowerQuery.contains("morgen")) {
            matches.add(new RagMatch("Grammar_WordOrder", "Rule: In Dutch, time usually precedes location, and the verb comes second (V2 rule). E.g. 'Ik ga morgen naar school'."));
        }
        
        List<RagMatch> result = matches.isEmpty() ? List.of() : matches.subList(0, Math.min(matches.size(), maxItems));
        log.debug("RAG retrieve completed matchCount={} maxItems={}", result.size(), maxItems);
        return result;
    }

    public String buildContextBlock(List<RagMatch> matches, int maxChars) {
        if (matches == null || matches.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("--- RETRIEVED LEARNING CONTEXT ---\n");
        for (RagMatch m : matches) {
            if (sb.length() + m.getText().length() > maxChars) break;
            sb.append("- ").append(m.getText()).append("\n");
        }
        sb.append("----------------------------------\n");
        log.debug("RAG context block created sourceCount={} maxChars={}", matches.size(), maxChars);
        return sb.toString();
    }
}
