package com.bankingplatform.opsagent.tools;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
    }

    public Optional<AgentTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }

    public List<Map<String, Object>> catalog() {
        return tools.values().stream().map(t -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", t.name());
            entry.put("description", t.description());
            entry.put("parameters", t.parameterSchema());
            return entry;
        }).collect(Collectors.toList());
    }

    public ToolResult invoke(String name, Map<String, Object> arguments) {
        return get(name)
            .map(t -> t.execute(arguments == null ? Map.of() : arguments))
            .orElseGet(() -> ToolResult.fail("Unknown tool: " + name));
    }
}
