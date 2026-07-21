package com.bankingplatform.notification.email;

import com.bankingplatform.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Email dispatcher for Harbor Bank ops alerts.
 * <p>
 * When SMTP is configured (spring.mail.host via SMTP_HOST) and email is
 * enabled, sends real mail to the configured ops recipients. Otherwise logs a
 * structured delivery record so the pipeline stays end-to-end verifiable
 * without credentials, mirroring {@code PushNotificationDispatcher}.
 */
@Component
public class EmailNotificationDispatcher {

    public record Result(String mode, int recipients, boolean success, String detail) {}

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationDispatcher.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationProperties properties;
    private final String smtpHost;

    public EmailNotificationDispatcher(ObjectProvider<JavaMailSender> mailSenderProvider,
                                       NotificationProperties properties,
                                       @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}") String smtpHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
        this.smtpHost = smtpHost;
    }

    public Result send(String subject, String body) {
        List<String> recipients = properties.resolvedOpsEmailRecipients();
        JavaMailSender mailSender = smtpHost == null || smtpHost.isBlank() ? null : mailSenderProvider.getIfAvailable();

        if (!properties.isEmailEnabled() || mailSender == null || recipients.isEmpty()) {
            String reason = !properties.isEmailEnabled() ? "email disabled (EMAIL_ENABLED=false)"
                : mailSender == null ? "SMTP not configured (SMTP_HOST unset)"
                : "no ops recipients configured (OPS_EMAIL_RECIPIENTS unset)";
            log.info("ops_email_logged_only reason=\"{}\" subject={} body={}", reason, subject, body);
            return new Result("LOGGED", recipients.size(), true, reason);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getOpsEmailFrom());
            message.setTo(recipients.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("ops_email_sent recipients={} subject={}", recipients.size(), subject);
            return new Result("SMTP", recipients.size(), true, "Delivered to " + recipients.size() + " recipient(s)");
        } catch (Exception ex) {
            log.error("ops_email_failed subject={} reason={}", subject, ex.getMessage());
            return new Result("SMTP", recipients.size(), false, "SMTP send failed: " + ex.getMessage());
        }
    }
}
