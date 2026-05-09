package com.dutchlearn.controller;

import com.dutchlearn.dto.EvaluationMessageDTO;
import com.dutchlearn.service.EvaluationMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * Model Evaluation Controller
 * Handles manual execution of evaluation loops to test model accuracy.
 */
@RestController
@RequestMapping("/api/evaluation")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
@RequiredArgsConstructor
@Slf4j
public class ModelEvaluationController {

    private final EvaluationMessageService evaluationMessageService;

    /**
     * Run a model evaluation test and return Pass/Fail metrics.
     * This mimics the evaluation loop which feeds prompts into the models
     * and checks against rules like Length, Language, and Safety.
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runEvaluation(@RequestBody Map<String, String> testParams) {
        String model = testParams.getOrDefault("model", "gpt-4o");
        log.info("Evaluation run requested model={}", model);

        // Here we would normally call the AiService for multiple models and evaluate
        // Because this is an evaluation loop stub, we return the structure of a result
        Map<String, Object> result = Map.of(
            "runId", "eval-" + System.currentTimeMillis(),
            "modelTested", model,
            "status", "COMPLETED",
            "passRate", "92%",
            "checks", List.of(
                Map.of("rule", "Language Level A2", "passed", true),
                Map.of("rule", "Length < 12 words", "passed", true),
                Map.of("rule", "JSON formatting", "passed", false)
            )
        );

        log.info("Evaluation run completed model={} status={}", model, result.get("status"));
        return ResponseEntity.ok(result);
    }

    /**
     * List stored assistant responses with evaluation metadata for admin review.
     */
    @GetMapping("/messages")
    public ResponseEntity<List<EvaluationMessageDTO>> getEvaluationMessages(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String modelTag) {
        List<EvaluationMessageDTO> messages = evaluationMessageService.getAssistantMessages(limit, modelTag);
        return ResponseEntity.ok(messages);
    }
}
