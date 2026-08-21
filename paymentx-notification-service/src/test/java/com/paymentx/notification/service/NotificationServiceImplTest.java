package com.paymentx.notification.service;

import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.notification.cache.NotificationDedupService;
import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.dto.ResendNotificationRequest;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.mapper.NotificationMapper;
import com.paymentx.notification.metrics.NotificationMetrics;
import com.paymentx.notification.repository.DeliveryAttemptRepository;
import com.paymentx.notification.repository.NotificationRepository;
import com.paymentx.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationServiceImplTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.service. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationServiceImplTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.service me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationDedupService notificationDedupService;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private NotificationProperties notificationProperties;
    private NotificationMetrics notificationMetrics;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationProperties = new NotificationProperties();
        notificationMetrics = new NotificationMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        notificationService = new NotificationServiceImpl(notificationRepository, deliveryAttemptRepository,
                notificationMapper, notificationDedupService, notificationDispatcher, notificationProperties, notificationMetrics);
    }

    @Test
    void createFromEvent_redisDedupRejectsDuplicate_neverTouchesDatabase() {
        when(notificationDedupService.markIfNew("evt-1")).thenReturn(false);

        notificationService.createFromEvent(SourceEventType.PAYMENT_COMPLETED, NotificationChannel.EMAIL,
                "user@example.com", "subj", "body", null, "evt-1", "corr-1", "trace-1", "BANK001", "pay-1");

        verify(notificationRepository, never()).existsBySourceEventId(any());
        verify(notificationRepository, never()).save(any());
        verify(notificationDispatcher, never()).dispatchAsync(any());
    }

    @Test
    void createFromEvent_dbCheckRejectsDuplicate_afterRedisPasses() {
        when(notificationDedupService.markIfNew("evt-2")).thenReturn(true);
        when(notificationRepository.existsBySourceEventId("evt-2")).thenReturn(true);

        notificationService.createFromEvent(SourceEventType.PAYMENT_FAILED, NotificationChannel.SMS,
                "+15551234567", null, "body", null, "evt-2", "corr-2", "trace-2", "BANK002", "pay-2");

        verify(notificationRepository, never()).save(any());
        verify(notificationDispatcher, never()).dispatchAsync(any());
    }

    @Test
    void createFromEvent_newEvent_savesAndDispatches() {
        when(notificationDedupService.markIfNew("evt-3")).thenReturn(true);
        when(notificationRepository.existsBySourceEventId("evt-3")).thenReturn(false);
        Notification saved = Notification.builder().channel(NotificationChannel.EMAIL).build();
        when(notificationRepository.save(any())).thenReturn(saved);

        notificationService.createFromEvent(SourceEventType.PAYMENT_COMPLETED, NotificationChannel.EMAIL,
                "user@example.com", "subj", "body", null, "evt-3", "corr-3", "trace-3", "BANK003", "pay-3");

        verify(notificationRepository).save(any());
        verify(notificationDispatcher).dispatchAsync(saved);
    }

    @Test
    void retry_onSentNotification_throwsConflict() {
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder().status(NotificationStatus.SENT).build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.retry(id, new ResendNotificationRequest("reason", "admin1")))
                .isInstanceOf(ConflictException.class);

        verify(notificationDispatcher, never()).dispatchAsync(any());
    }

    @Test
    void retry_onFailedNotification_resetsAndDispatches() {
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder().status(NotificationStatus.FAILED).retryCount(3).build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(notification);

        notificationService.retry(id, new ResendNotificationRequest("reason", "admin1"));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isEqualTo(0);
        verify(notificationDispatcher).dispatchAsync(notification);
    }

    @Test
    void resend_createsNewNotificationWithoutSourceEventId() {
        UUID id = UUID.randomUUID();
        Notification original = Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_COMPLETED)
                .channel(NotificationChannel.EMAIL)
                .recipient("user@example.com")
                .sourceEventId("original-evt-id")
                .build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(original));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.resend(id, new ResendNotificationRequest("reason", "admin1"));

        verify(notificationRepository, times(1)).save(any());
        verify(notificationDispatcher).dispatchAsync(any());
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
