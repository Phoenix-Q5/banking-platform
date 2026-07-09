package com.bankingplatform.opsagent.llm;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicReasoningEngineTest {

    private final HeuristicReasoningEngine engine = new HeuristicReasoningEngine();

    @Test
    void firstRoundQueriesServiceHealth() {
        Incident incident = new Incident();
        incident.setTitle("Circuit breaker open on transaction-service");
        incident.setAffectedService("transaction-service");
        incident.setCategory("resiliency");

        LlmDecision decision = engine.next(incident, List.of(), List.of(), 0);

        assertEquals(LlmDecision.Kind.TOOL_CALL, decision.getKind());
        assertEquals("service_health", decision.getToolName());
        assertEquals("transaction-service", decision.getToolArguments().get("service"));
    }

    @Test
    void finalRoundProducesCircuitBreakerPlaybook() {
        Incident incident = new Incident();
        incident.setTitle("CircuitBreakerOpen");
        incident.setSummary("Circuit breaker open on transaction-service");
        incident.setAffectedService("transaction-service");
        incident.setCategory("resiliency");

        List<ToolResult> prior = new ArrayList<>();
        prior.add(ToolResult.ok("transaction-service health status=UP", Map.of("status", "UP")));
        prior.add(ToolResult.ok("Prometheus returned 1 series", Map.of()));
        prior.add(ToolResult.ok("Fetched circuit breaker state", Map.of("state", "open")));

        LlmDecision decision = engine.next(incident, List.of(), prior, 5);

        assertEquals(LlmDecision.Kind.FINAL, decision.getKind());
        assertEquals("circuit-breaker-open", decision.getPlaybookHint());
        assertNotNull(decision.getFinalAnswer());
        assertTrue(decision.getFinalAnswer().contains("Incident Report"));
        assertTrue(decision.getRecommendedActions().size() > 0);
    }
}
