package com.bankingplatform.opsagent.service;

import com.bankingplatform.opsagent.config.OpsAgentProperties;
import com.bankingplatform.opsagent.model.Incident;
import com.bankingplatform.opsagent.model.RestartRequest;
import com.bankingplatform.opsagent.persistence.IncidentPersistence;
import com.bankingplatform.opsagent.tools.ToolRegistry;
import com.bankingplatform.opsagent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RestartServiceTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private OpsEmailNotifier emailNotifier;

    private IncidentStore incidentStore;
    private RestartService service;

    @BeforeEach
    void setUp() {
        OpsAgentProperties properties = new OpsAgentProperties();
        properties.getServices().put("account-service", "http://localhost:8081");
        properties.getServices().put("transaction-service", "http://localhost:8082");
        incidentStore = new IncidentStore();
        lenient().when(toolRegistry.invoke(anyString(), anyMap()))
            .thenReturn(ToolResult.ok("health UP", Map.of()));
        lenient().when(emailNotifier.notifyRestart(any(), any(), any(), any(), any()))
            .thenReturn(Map.of("success", true));

        @SuppressWarnings("unchecked")
        ObjectProvider<IncidentPersistence> noPersistence = mock(ObjectProvider.class);
        service = new RestartService(properties, incidentStore, toolRegistry, emailNotifier, noPersistence);
    }

    @Test
    void requestConfirmLifecycle() {
        RestartRequest request = service.request("account-service", "DB pool exhausted", null, "demo.support");
        assertEquals(RestartRequest.Status.PENDING_CONFIRMATION, request.getStatus());
        assertEquals("demo.support", request.getRequestedBy());

        RestartRequest confirmed = service.confirm(request.getId(), "demo.admin");
        assertEquals(RestartRequest.Status.CONFIRMED, confirmed.getStatus());
        assertEquals("demo.admin", confirmed.getDecidedBy());
        assertNotNull(confirmed.getDecidedAt());
        assertTrue(confirmed.getNote().contains("docker-compose restart account-service"));
    }

    @Test
    void rejectsUnknownService() {
        assertThrows(IllegalArgumentException.class,
            () -> service.request("keycloak", "restart auth", null, "demo.admin"));
    }

    @Test
    void rejectsMissingReason() {
        assertThrows(IllegalArgumentException.class,
            () -> service.request("account-service", " ", null, "demo.admin"));
    }

    @Test
    void rejectsDuplicatePendingRequestForSameService() {
        service.request("account-service", "first", null, "demo.admin");
        assertThrows(IllegalStateException.class,
            () -> service.request("account-service", "second", null, "demo.admin"));
    }

    @Test
    void cancelledRequestCannotBeConfirmed() {
        RestartRequest request = service.request("transaction-service", "stuck consumers", null, "demo.support");
        service.cancel(request.getId(), "demo.admin", "recovered on its own");

        assertEquals(RestartRequest.Status.CANCELLED, request.getStatus());
        assertThrows(IllegalStateException.class, () -> service.confirm(request.getId(), "demo.admin"));
    }

    @Test
    void auditsLinkedIncident() {
        Incident incident = new Incident();
        incident.setTitle("account-service down");
        incidentStore.save(incident);

        RestartRequest request = service.request("account-service", "service down", incident.getId(), "demo.support");
        service.confirm(request.getId(), "demo.admin");

        assertEquals(2, incident.getAuditTrail().size());
        assertEquals("RESTART_REQUESTED", incident.getAuditTrail().get(0).getAction());
        assertEquals("RESTART_CONFIRMED", incident.getAuditTrail().get(1).getAction());
    }
}
