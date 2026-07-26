package com.bankingplatform.aiagent.controller;

import com.bankingplatform.aiagent.model.AnalyzeRequest;
import com.bankingplatform.aiagent.model.AskRequest;
import com.bankingplatform.aiagent.service.CodeAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    private final CodeAgentService codeAgentService;

    public AiAgentController(CodeAgentService codeAgentService) {
        this.codeAgentService = codeAgentService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return codeAgentService.health();
    }

    @PostMapping("/index")
    public Map<String, Object> index() {
        return codeAgentService.reindex();
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@Valid @RequestBody AskRequest request) {
        try {
            return codeAgentService.ask(request.getQuestion(), request.getTopK());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        }
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody(required = false) AnalyzeRequest request) {
        try {
            String focus = request == null ? null : request.getFocus();
            return codeAgentService.analyze(focus);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        }
    }
}
