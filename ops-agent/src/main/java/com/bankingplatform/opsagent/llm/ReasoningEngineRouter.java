package com.bankingplatform.opsagent.llm;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReasoningEngineRouter implements ReasoningEngine {

    private final OpsAgentProperties properties;
    private final OpenAiCompatibleReasoningEngine openAi;
    private final HeuristicReasoningEngine heuristic;

    public ReasoningEngineRouter(OpsAgentProperties properties,
                                 OpenAiCompatibleReasoningEngine openAi,
                                 HeuristicReasoningEngine heuristic) {
        this.properties = properties;
        this.openAi = openAi;
        this.heuristic = heuristic;
    }

    @Override
    public String name() {
        return active().name();
    }

    @Override
    public LlmDecision next(Incident incident, List<Map<String, Object>> toolCatalog, List<ToolResult> priorResults, int round) {
        return active().next(incident, toolCatalog, priorResults, round);
    }

    public ReasoningEngine active() {
        if (properties.getLlm().isEnabled() && openAi.isAvailable()) {
            return openAi;
        }
        return heuristic;
    }
}
