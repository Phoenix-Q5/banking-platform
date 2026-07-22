package com.bankingplatform.customer.email;

import com.bankingplatform.customer.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends the newly issued support PIN by email for tracing / customer delivery.
 * Uses the same SMTP_* settings as Keycloak and notification-service.
 */
@Component
public class SupportPinMailer {

    private static final Logger log = LoggerFactory.getLogger(SupportPinMailer.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String smtpHost;
    private final String from;
    private final String opsRecipients;
    private final boolean emailEnabled;

    public SupportPinMailer(ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${spring.mail.host:}") String smtpHost,
                            @Value("${harbor.mail.from:${spring.mail.username:noreply@harborbank.local}}") String from,
                            @Value("${harbor.mail.ops-recipients:}") String opsRecipients,
                            @Value("${harbor.mail.enabled:true}") boolean emailEnabled) {
        this.mailSenderProvider = mailSenderProvider;
        this.smtpHost = smtpHost;
        this.from = from;
        this.opsRecipients = opsRecipients;
        this.emailEnabled = emailEnabled;
    }

    public void sendPinIssued(Customer customer, String pin) {
        Set<String> recipients = new LinkedHashSet<>();
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            recipients.add(customer.getEmail().trim());
        }
        if (opsRecipients != null && !opsRecipients.isBlank()) {
            recipients.addAll(Arrays.stream(opsRecipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet()));
        }

        String subject = "Harbor Bank — support PIN issued";
        String body = """
            Harbor Bank support PIN

            Customer : %s %s
            Email    : %s
            ID       : %s

            Support PIN: %s

            Share this PIN only with Harbor Bank support when they ask for identity verification.
            You can change it anytime under Profile → Support PIN.
            """.formatted(
                customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getId(), pin
            );

        JavaMailSender mailSender = smtpHost == null || smtpHost.isBlank()
            ? null : mailSenderProvider.getIfAvailable();

        if (!emailEnabled || mailSender == null || recipients.isEmpty()) {
            String reason = !emailEnabled ? "email disabled"
                : mailSender == null ? "SMTP not configured (SMTP_HOST unset)"
                : "no recipients";
            // Always log the PIN in structured logs when mail cannot be sent, so
            // operators can still recover it from Loki / docker logs in demos.
            log.info("support_pin_email_logged_only reason=\"{}\" customerId={} email={} pin={} intendedRecipients={}",
                reason, customer.getId(), customer.getEmail(), pin, recipients);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipients.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("support_pin_email_sent customerId={} recipients={}", customer.getId(), recipients.size());
        } catch (Exception ex) {
            log.error("support_pin_email_failed customerId={} reason={} — PIN for recovery: {}",
                customer.getId(), ex.getMessage(), pin);
        }
    }
}
