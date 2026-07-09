package com.bankingplatform.notification.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.notification.config.NotificationProperties;
import com.bankingplatform.notification.model.Notification;
import com.bankingplatform.notification.push.PushNotificationDispatcher;
import com.bankingplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertNotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock PushNotificationDispatcher pushDispatcher;
    NotificationProperties properties;
    AlertNotificationService service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setDefaultChannels("IN_APP,PUSH");
        properties.setPushEnabled(true);
        service = new AlertNotificationService(notificationRepository, pushDispatcher, properties);
        when(notificationRepository.existsByEventIdAndChannel(any(), any())).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pushDispatcher.dispatch(any())).thenReturn(1);
    }

    @Test
    void transferCompletedCreatesAlertsForSenderAndReceiver() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        DomainEvent event = DomainEvent.of(
            EventTypes.TRANSFER_COMPLETED,
            "transaction",
            UUID.randomUUID().toString(),
            from.toString(),
            "transaction-service",
            Map.of(
                "fromCustomerId", from.toString(),
                "toCustomerId", to.toString(),
                "fromAccountId", UUID.randomUUID().toString(),
                "toAccountId", UUID.randomUUID().toString(),
                "amount", "25.00",
                "currency", "USD"
            )
        );

        List<Notification> created = service.handleDomainEvent(event);
        assertEquals(4, created.size()); // 2 customers × 2 channels
        assertTrue(created.stream().anyMatch(n -> n.getCustomerId().equals(from)));
        assertTrue(created.stream().anyMatch(n -> n.getCustomerId().equals(to)));
        assertTrue(created.stream().anyMatch(n -> n.getChannel() == Notification.Channel.PUSH));
        verify(pushDispatcher, atLeastOnce()).dispatch(any());
    }

    @Test
    void unknownEventIgnored() {
        DomainEvent event = DomainEvent.of("something.else", "x", "1", UUID.randomUUID().toString(), "test", Map.of());
        assertTrue(service.handleDomainEvent(event).isEmpty());
    }
}
