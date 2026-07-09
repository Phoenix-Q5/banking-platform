
package com.bankingplatform.audit.controller;
import com.bankingplatform.audit.dto.AuditDtos.*;
import com.bankingplatform.audit.model.AuditEvent;
import com.bankingplatform.audit.repository.AuditEventRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.UUID;

@RestController @RequestMapping("/api/audit")
public class AuditController {
    private static final Logger log = LoggerFactory.getLogger(AuditController.class);
    private final AuditEventRepository repository;
    public AuditController(AuditEventRepository repository){ this.repository=repository; }

    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED)
    public AuditEventResponse create(@Valid @RequestBody CreateAuditEventRequest request){
        AuditEvent e = new AuditEvent();
        e.setActor(request.actor()); e.setAction(request.action());
        e.setResourceType(request.resourceType()); e.setResourceId(request.resourceId());
        e.setCustomerId(request.customerId()); e.setDetails(request.details()); e.setIpAddress(request.ipAddress());
        AuditEvent saved = repository.save(e);
        log.info("audit_event action={} resourceType={} resourceId={}", saved.getAction(), saved.getResourceType(), saved.getResourceId());
        return AuditEventResponse.from(saved);
    }

    @GetMapping("/events")
    public List<AuditEventResponse> list(@RequestParam(required=false) UUID customerId,
                                         @RequestParam(required=false) String resourceType,
                                         @RequestParam(required=false) String resourceId){
        if (customerId != null) return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(AuditEventResponse::from).toList();
        if (resourceType != null && resourceId != null)
            return repository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType, resourceId).stream().map(AuditEventResponse::from).toList();
        return repository.findTop100ByOrderByCreatedAtDesc().stream().map(AuditEventResponse::from).toList();
    }
}
