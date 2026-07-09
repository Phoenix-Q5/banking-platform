package com.bankingplatform.opsagent.llm;

import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.tools.ToolResult;

import java.util.List;
import java.util.Map;

public interface ReasoningEngine {

    String name();

    LlmDecision next(Incident incident, List<Map<String, Object>> toolCatalog, List<ToolResult> priorResults, int round);
}
