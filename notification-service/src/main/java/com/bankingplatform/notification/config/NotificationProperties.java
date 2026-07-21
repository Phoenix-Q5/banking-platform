package com.bankingplatform.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "harbor.notifications")
public class NotificationProperties {

    private boolean pushEnabled = true;
    private boolean emailEnabled = false;
    private boolean smsEnabled = false;
    /** Comma-separated channel list, e.g. IN_APP,PUSH */
    private String defaultChannels = "IN_APP,PUSH";
    /** Comma-separated ops team mailboxes for high-priority service emails. */
    private String opsEmailRecipients = "";
    /** From address for outbound ops emails. */
    private String opsEmailFrom = "noreply@harborbank.local";

    public boolean isPushEnabled() { return pushEnabled; }
    public void setPushEnabled(boolean pushEnabled) { this.pushEnabled = pushEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isSmsEnabled() { return smsEnabled; }
    public void setSmsEnabled(boolean smsEnabled) { this.smsEnabled = smsEnabled; }
    public String getOpsEmailRecipients() { return opsEmailRecipients; }
    public void setOpsEmailRecipients(String opsEmailRecipients) { this.opsEmailRecipients = opsEmailRecipients; }
    public String getOpsEmailFrom() { return opsEmailFrom; }
    public void setOpsEmailFrom(String opsEmailFrom) { this.opsEmailFrom = opsEmailFrom; }

    public List<String> resolvedOpsEmailRecipients() {
        if (opsEmailRecipients == null || opsEmailRecipients.isBlank()) {
            return List.of();
        }
        return Arrays.stream(opsEmailRecipients.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public String getDefaultChannels() { return defaultChannels; }
    public void setDefaultChannels(String defaultChannels) { this.defaultChannels = defaultChannels; }

    public List<String> resolvedChannels() {
        if (defaultChannels == null || defaultChannels.isBlank()) {
            return List.of("IN_APP");
        }
        return Arrays.stream(defaultChannels.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
