package com.paymentx.notification.service;

import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.entity.DeliveryAttempt;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.metrics.NotificationMetrics;
import com.paymentx.notification.repository.DeliveryAttemptRepository;
import com.paymentx.notification.repository.NotificationRepository;
import com.paymentx.notification.service.channel.NotificationChannelFactory;
import com.paymentx.notification.service.channel.NotificationDeliveryException;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * WHY @Async lives HERE (a separate bean), not on a method inside
 * NotificationServiceImpl calling itself: Spring's @Async proxying only
 * intercepts calls that arrive THROUGH the Spring-managed proxy - a
 * method calling another @Async method on `this` within the same class
 * bypasses the proxy entirely and runs synchronously, a very common,
 * easy-to-miss Spring gotcha. NotificationServiceImpl injects this as a
 * separate bean and calls dispatch() through it, guaranteeing the proxy
 * is actually invoked.
 *
 * WHY each attempt is its own REQUIRES_NEW transaction, not part of the
 * caller's transaction: a delivery ATTEMPT (and its resulting DB writes -
 * status, DeliveryAttempt row, retry schedule) must be durably recorded
 * regardless of whether the caller's broader transaction later succeeds
 * or fails - the same "recording that something was attempted must
 * survive independently" principle Payment Service's
 * IdempotencyService.REQUIRES_NEW comment documents.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationDispatcher is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationDispatcher PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationDispatcher {

    private final NotificationChannelFactory notificationChannelFactory;
    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationMetrics notificationMetrics;
    private final NotificationProperties notificationProperties;

    @Async("notificationDispatchExecutor")
    public void dispatchAsync(Notification notification) {
        dispatch(notification);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Notification notification) {
        Timer.Sample timerSample = notificationMetrics.startTimer();

        var handlerOpt = notificationChannelFactory.getHandler(notification.getChannel());
        if (handlerOpt.isEmpty()) {
            log.error("No handler registered for channel={} notificationId={}", notification.getChannel(), notification.getId());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason("No handler registered for channel " + notification.getChannel());
            notificationRepository.save(notification);
            return;
        }

        notification.setStatus(NotificationStatus.SENDING);
        notification.setLastAttemptAt(OffsetDateTime.now());
        int attemptNumber = notification.getRetryCount() + 1;

        try {
            handlerOpt.get().send(notification);

            notification.setStatus(NotificationStatus.SENT);
            notification.setFailureReason(null);
            recordAttempt(notification, attemptNumber, NotificationStatus.SENT, null, null);
            notificationMetrics.recordChannelSuccess(notification.getChannel());

        } catch (NotificationDeliveryException e) {
            notificationMetrics.recordChannelFailure(notification.getChannel());
            notification.setFailureReason(e.getMessage());
            notification.setRetryCount(attemptNumber);

            var retryProps = notificationProperties.getRetry();
            if (attemptNumber >= retryProps.getMaxAttempts()) {
                notification.setStatus(NotificationStatus.DEAD_LETTERED);
                notificationMetrics.recordDeadLettered(notification.getChannel());
                log.error("Notification dead-lettered after {} attempts id={} channel={}",
                        attemptNumber, notification.getId(), notification.getChannel());
            } else {
                notification.setStatus(NotificationStatus.RETRYING);
                notification.setNextRetryAt(calculateNextRetry(attemptNumber, retryProps));
                notificationMetrics.recordRetry(notification.getChannel());
            }

            recordAttempt(notification, attemptNumber, NotificationStatus.FAILED, e.getMessage(), e.getResponseCode());
        }

        notificationRepository.save(notification);
        notificationMetrics.recordProcessingTime(timerSample, notification.getChannel());
    }

    private OffsetDateTime calculateNextRetry(int attemptNumber, NotificationProperties.Retry retryProps) {
        long delayMillis = (long) (retryProps.getInitialIntervalMillis() * Math.pow(retryProps.getMultiplier(), attemptNumber - 1));
        delayMillis = Math.min(delayMillis, retryProps.getMaxIntervalMillis());
        return OffsetDateTime.now().plusNanos(delayMillis * 1_000_000L);
    }

    private void recordAttempt(Notification notification, int attemptNumber, NotificationStatus status,
                                String errorMessage, Integer responseCode) {
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .notificationId(notification.getId())
                .attemptNumber(attemptNumber)
                .status(status)
                .errorMessage(errorMessage)
                .responseCode(responseCode)
                .attemptedAt(OffsetDateTime.now())
                .build();
        deliveryAttemptRepository.save(attempt);
    }
}
