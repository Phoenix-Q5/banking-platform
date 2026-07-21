package com.bankingplatform.customer.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestClient restClient;
    private final String realm;
    private final String adminUser;
    private final String adminPassword;

    public KeycloakAdminClient(
        @Value("${harbor.keycloak.server-url}") String serverUrl,
        @Value("${harbor.keycloak.realm}") String realm,
        @Value("${harbor.keycloak.admin-username}") String adminUser,
        @Value("${harbor.keycloak.admin-password}") String adminPassword
    ) {
        this.realm = realm;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.restClient = RestClient.builder().baseUrl(serverUrl).build();
    }

    public String createUser(CreateUserCommand cmd) {
        String token = adminToken();
        try {
            var response = restClient.post()
                .uri("/admin/realms/{realm}/users", realm)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "username", cmd.username(),
                    "email", cmd.email(),
                    "firstName", cmd.firstName(),
                    "lastName", cmd.lastName(),
                    "enabled", true,
                    "emailVerified", true,
                    "credentials", List.of(Map.of(
                        "type", "password",
                        "value", cmd.password(),
                        "temporary", false
                    ))
                ))
                .retrieve()
                .toBodilessEntity();

            String location = response.getHeaders().getFirst("Location");
            if (location == null || location.isBlank()) {
                throw new IllegalStateException("Keycloak did not return user Location header");
            }
            String userId = location.substring(location.lastIndexOf('/') + 1);
            assignRealmRole(token, userId, "CUSTOMER");
            log.info("keycloak_user_created username={} userId={}", cmd.username(), userId);
            return userId;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                throw new IllegalArgumentException("Username or email already exists");
            }
            throw new IllegalStateException("Failed to create Keycloak user: " + ex.getResponseBodyAsString(), ex);
        }
    }

    public void deleteUser(String userId) {
        if (userId == null || userId.isBlank()) return;
        try {
            String token = adminToken();
            restClient.delete()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
            log.info("keycloak_user_deleted userId={}", userId);
        } catch (Exception ex) {
            log.warn("keycloak_user_delete_failed userId={} error={}", userId, ex.getMessage());
        }
    }

    private void assignRealmRole(String token, String userId, String roleName) {
        RealmRole role = restClient.get()
            .uri("/admin/realms/{realm}/roles/{role}", realm, roleName)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(RealmRole.class);
        if (role == null || role.id() == null) {
            throw new IllegalStateException("Realm role not found: " + roleName);
        }
        restClient.post()
            .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(List.of(Map.of(
                "id", role.id(),
                "name", role.name()
            )))
            .retrieve()
            .toBodilessEntity();
    }

    private String adminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUser);
        form.add("password", adminPassword);
        try {
            JsonNode node = restClient.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
            if (node == null || !node.hasNonNull("access_token")) {
                throw new IllegalStateException("No access_token from Keycloak admin-cli grant");
            }
            return node.get("access_token").asText();
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                "Keycloak admin token failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    public record CreateUserCommand(
        String username,
        String password,
        String email,
        String firstName,
        String lastName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RealmRole(String id, String name) {}
}
