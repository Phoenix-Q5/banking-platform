package com.bankingplatform.opsagent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Two mutually exclusive chains:
 *  - ops-agent.security.enabled=true  -> Keycloak JWT resource server; incident
 *    workflow requires ADMIN or SUPPORT, restart confirmation requires ADMIN.
 *  - ops-agent.security.enabled=false -> everything open (local/offline demo).
 *
 * The Alertmanager webhook stays open in both modes (Alertmanager cannot obtain
 * user tokens; network isolation is the boundary there, as for the other
 * internal S2S endpoints in this platform).
 */
@Configuration
public class SecurityConfig {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SUPPORT = "SUPPORT";

    @Configuration
    @ConditionalOnProperty(prefix = "ops-agent.security", name = "enabled", havingValue = "true")
    public static class SecuredChain {

        @Bean
        public SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/index.html", "/command-center.html", "/favicon.ico").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/agent/webhooks/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/agent/health-summary").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/agent/monitoring/snapshot").hasAnyRole(ROLE_ADMIN, ROLE_SUPPORT)
                    .requestMatchers("/api/agent/restart-requests/*/confirm").hasRole(ROLE_ADMIN)
                    .requestMatchers("/api/agent/**").hasAnyRole(ROLE_ADMIN, ROLE_SUPPORT)
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(keycloakRealmRoleConverter())));
            return http.build();
        }

        private Converter<Jwt, AbstractAuthenticationToken> keycloakRealmRoleConverter() {
            return jwt -> {
                Collection<GrantedAuthority> authorities = extractRealmRoles(jwt).stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
                return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
            };
        }

        @SuppressWarnings("unchecked")
        private List<String> extractRealmRoles(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream().map(String::valueOf).collect(Collectors.toList());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ops-agent.security", name = "enabled", havingValue = "false", matchIfMissing = true)
    public static class OpenChain {

        @Bean
        public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
