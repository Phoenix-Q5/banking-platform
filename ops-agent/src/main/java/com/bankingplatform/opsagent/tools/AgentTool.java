package com.bankingplatform.opsagent.tools;

import java.util.Map;

/**
 * A single tool the LLM (or heuristic engine) can invoke while investigating.
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameterSchema();

    ToolResult execute(Map<String, Object> arguments);
}
