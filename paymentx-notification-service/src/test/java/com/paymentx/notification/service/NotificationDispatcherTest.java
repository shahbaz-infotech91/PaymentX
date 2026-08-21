package com.paymentx.notification.service;

import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.metrics.NotificationMetrics;
import com.paymentx.notification.repository.DeliveryAttemptRepository;
import com.paymentx.notification.repository.NotificationRepository;
import com.paymentx.notification.service.channel.NotificationChannelFactory;
import com.paymentx.notification.service.channel.NotificationChannelHandler;
import com.paymentx.notification.service.channel.NotificationDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationDispatcherTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.service. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationDispatcherTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.service me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class NotificationDispatcherTest {

    @Mock
    private NotificationChannelFactory notificationChannelFactory;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    private NotificationMetrics notificationMetrics;
    private NotificationProperties notificationProperties;
    private NotificationDispatcher dispatcher;
    private NotificationChannelHandler mockHandler;

    @BeforeEach
    void setUp() {
        notificationMetrics = new NotificationMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        notificationProperties = new NotificationProperties();
        dispatcher = new NotificationDispatcher(notificationChannelFactory, notificationRepository,
                deliveryAttemptRepository, notificationMetrics, notificationProperties);
        mockHandler = mock(NotificationChannelHandler.class);
    }

    @Test
    void dispatch_successfulSend_marksSentAndRecordsAttempt() throws NotificationDeliveryException {
        Notification notification = Notification.builder()
                .channel(NotificationChannel.EMAIL).status(NotificationStatus.PENDING).retryCount(0).build();
        when(notificationChannelFactory.getHandler(NotificationChannel.EMAIL)).thenReturn(Optional.of(mockHandler));
        doNothing().when(mockHandler).send(notification);

        dispatcher.dispatch(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(deliveryAttemptRepository).save(any());
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatch_failureUnderRetryBudget_marksRetryingWithNextRetryAt() throws NotificationDeliveryException {
        Notification notification = Notification.builder()
                .channel(NotificationChannel.SMS).status(NotificationStatus.PENDING).retryCount(0).build();
        when(notificationChannelFactory.getHandler(NotificationChannel.SMS)).thenReturn(Optional.of(mockHandler));
        doThrow(new NotificationDeliveryException("provider down", (Throwable) null))
                .when(mockHandler).send(notification);

        dispatcher.dispatch(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(notification.getNextRetryAt()).isNotNull();
        assertThat(notification.getRetryCount()).isEqualTo(1);
    }

    @Test
    void dispatch_failureAtMaxAttempts_deadLetters() throws NotificationDeliveryException {
        Notification notification = Notification.builder()
                .channel(NotificationChannel.WEBHOOK).status(NotificationStatus.RETRYING).retryCount(4).build();
        when(notificationChannelFactory.getHandler(NotificationChannel.WEBHOOK)).thenReturn(Optional.of(mockHandler));
        doThrow(new NotificationDeliveryException("still down", 503))
                .when(mockHandler).send(notification);

        dispatcher.dispatch(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
    }

    @Test
    void dispatch_noHandlerRegistered_marksFailedWithoutConsumingRetryBudget() {
        Notification notification = Notification.builder()
                .channel(NotificationChannel.PUSH).status(NotificationStatus.PENDING).retryCount(0).build();
        when(notificationChannelFactory.getHandler(NotificationChannel.PUSH)).thenReturn(Optional.empty());

        dispatcher.dispatch(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).contains("No handler registered");
    }
}
