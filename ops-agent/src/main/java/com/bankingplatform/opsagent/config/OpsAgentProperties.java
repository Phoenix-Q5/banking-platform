package com.bankingplatform.opsagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ops-agent")
public class OpsAgentProperties {

    private String platformName = "banking-platform";
    private boolean autoInvestigate = true;
    private int maxToolRounds = 6;
    private String mitigationMode = "recommend";
    private Observability observability = new Observability();
    private Map<String, String> services = new LinkedHashMap<>();
    private Llm llm = new Llm();
    private Security security = new Security();
    private Persistence persistence = new Persistence();

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public boolean isAutoInvestigate() {
        return autoInvestigate;
    }

    public void setAutoInvestigate(boolean autoInvestigate) {
        this.autoInvestigate = autoInvestigate;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public String getMitigationMode() {
        return mitigationMode;
    }

    public void setMitigationMode(String mitigationMode) {
        this.mitigationMode = mitigationMode;
    }

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability;
    }

    public Map<String, String> getServices() {
        return services;
    }

    public void setServices(Map<String, String> services) {
        this.services = services;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public void setPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public static class Security {
        /** When true, privileged endpoints require a Keycloak JWT with ADMIN/SUPPORT roles. */
        private boolean enabled = false;
        /** Browser-facing Keycloak token endpoint used by the ops console login form. */
        private String keycloakTokenUrl = "http://localhost:8180/realms/banking/protocol/openid-connect/token";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeycloakTokenUrl() {
            return keycloakTokenUrl;
        }

        public void setKeycloakTokenUrl(String keycloakTokenUrl) {
            this.keycloakTokenUrl = keycloakTokenUrl;
        }
    }

    public static class Persistence {
        /**
         * When true, incidents and restart requests are persisted to PostgreSQL.
         * The datasource itself is configured via standard spring.datasource.* properties.
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Observability {
        private String prometheusUrl = "http://localhost:9090";
        private String lokiUrl = "http://localhost:3100";
        private String tempoUrl = "http://localhost:3200";

        public String getPrometheusUrl() {
            return prometheusUrl;
        }

        public void setPrometheusUrl(String prometheusUrl) {
            this.prometheusUrl = prometheusUrl;
        }

        public String getLokiUrl() {
            return lokiUrl;
        }

        public void setLokiUrl(String lokiUrl) {
            this.lokiUrl = lokiUrl;
        }

        public String getTempoUrl() {
            return tempoUrl;
        }

        public void setTempoUrl(String tempoUrl) {
            this.tempoUrl = tempoUrl;
        }
    }

    public static class Llm {
        private boolean enabled;
        private String provider = "ollama";
        private String baseUrl = "http://localhost:11434/v1";
        private String apiKey = "ollama";
        private String model = "llama3.2:1b";
        private double temperature = 0.2;
        private int timeoutSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
