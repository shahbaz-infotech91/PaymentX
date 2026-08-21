package com.paymentx.notification.service.impl;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.notification.cache.NotificationDedupService;
import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.dto.DeliveryAttemptResponse;
import com.paymentx.notification.dto.NotificationResponse;
import com.paymentx.notification.dto.NotificationSearchCriteria;
import com.paymentx.notification.dto.ResendNotificationRequest;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.mapper.NotificationMapper;
import com.paymentx.notification.metrics.NotificationMetrics;
import com.paymentx.notification.repository.DeliveryAttemptRepository;
import com.paymentx.notification.repository.NotificationRepository;
import com.paymentx.notification.repository.NotificationSpecifications;
import com.paymentx.notification.service.NotificationDispatcher;
import com.paymentx.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationServiceImpl is a service in the notification module of PaymentX. It lives in package com.paymentx.notification.service.impl and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationServiceImpl PaymentX ke notification module ka ek service hai. Ye com.paymentx.notification.service.impl package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationServiceImpl implements NotificationService {

    private static final Set<NotificationStatus> RETRYABLE_STATUSES = Set.of(NotificationStatus.FAILED, NotificationStatus.DEAD_LETTERED);

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationDedupService notificationDedupService;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationProperties notificationProperties;
    private final NotificationMetrics notificationMetrics;

    @Override
    public void createFromEvent(SourceEventType sourceEventType, NotificationChannel channel, String recipient,
                                 String subject, String body, String templateName,
                                 String sourceEventId, String correlationId, String traceId,
                                 String participantId, String paymentId) {

        if (sourceEventId != null && !notificationDedupService.markIfNew(sourceEventId)) {
            notificationMetrics.recordDedupSkipped();
            log.info("Skipping duplicate notification (Redis dedup) sourceEventId={}", sourceEventId);
            return;
        }

        if (sourceEventId != null && notificationRepository.existsBySourceEventId(sourceEventId)) {
            log.info("Skipping duplicate notification (DB check) sourceEventId={}", sourceEventId);
            return;
        }

        Notification notification = Notification.builder()
                .sourceEventType(sourceEventType)
                .channel(channel)
                .status(NotificationStatus.PENDING)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .templateName(templateName)
                .sourceEventId(sourceEventId)
                .correlationId(correlationId)
                .traceId(traceId)
                .participantId(participantId)
                .paymentId(paymentId)
                .retryCount(0)
                .maxRetries(notificationProperties.getRetry().getMaxAttempts())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created id={} sourceEventType={} channel={} recipient={}",
                saved.getId(), sourceEventType, channel, maskRecipient(channel, recipient));

        notificationDispatcher.dispatchAsync(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        return notificationMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(NotificationSearchCriteria criteria, int page, int size) {
        Specification<Notification> spec = Specification
                .allOf(NotificationSpecifications.hasStatus(criteria.status()))
                .and(NotificationSpecifications.hasChannel(criteria.channel()))
                .and(NotificationSpecifications.hasSourceEventType(criteria.sourceEventType()))
                .and(NotificationSpecifications.hasRecipient(criteria.recipient()))
                .and(NotificationSpecifications.hasParticipantId(criteria.participantId()))
                .and(NotificationSpecifications.createdAfter(criteria.fromDate()))
                .and(NotificationSpecifications.createdBefore(criteria.toDate()));

        var pageResult = notificationRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.of(
                pageResult.getContent().stream().map(notificationMapper::toResponse).toList(),
                pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAttemptResponse> getHistory(UUID notificationId) {
        findOrThrow(notificationId);
        return notificationMapper.toAttemptResponses(
                deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notificationId));
    }

    @Override
    public NotificationResponse retry(UUID id, ResendNotificationRequest request) {
        Notification notification = findOrThrow(id);

        if (!RETRYABLE_STATUSES.contains(notification.getStatus())) {
            throw new ConflictException("NOTIFICATION_NOT_RETRYABLE",
                    "Notification '" + id + "' is in status " + notification.getStatus()
                            + " and is not eligible for retry. Eligible statuses: " + RETRYABLE_STATUSES);
        }

        notification.setStatus(NotificationStatus.PENDING);
        notification.setRetryCount(0);
        notification.setNextRetryAt(null);
        notification.setFailureReason(null);
        Notification saved = notificationRepository.save(notification);

        log.info("Notification manually retried id={} requestedBy={} reason={}", id, request.requestedBy(), request.reason());
        notificationDispatcher.dispatchAsync(saved);

        return notificationMapper.toResponse(saved);
    }

    @Override
    public NotificationResponse resend(UUID id, ResendNotificationRequest request) {
        Notification original = findOrThrow(id);

        Notification copy = Notification.builder()
                .sourceEventType(original.getSourceEventType())
                .channel(original.getChannel())
                .status(NotificationStatus.PENDING)
                .recipient(original.getRecipient())
                .subject(original.getSubject())
                .body(original.getBody())
                .templateName(original.getTemplateName())
                .sourceEventId(null)
                .correlationId(original.getCorrelationId())
                .traceId(original.getTraceId())
                .participantId(original.getParticipantId())
                .paymentId(original.getPaymentId())
                .retryCount(0)
                .maxRetries(notificationProperties.getRetry().getMaxAttempts())
                .build();

        Notification saved = notificationRepository.save(copy);
        log.info("Notification resent originalId={} newId={} requestedBy={} reason={}",
                id, saved.getId(), request.requestedBy(), request.reason());

        notificationDispatcher.dispatchAsync(saved);
        return notificationMapper.toResponse(saved);
    }

    private Notification findOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id.toString()));
    }

    private String maskRecipient(NotificationChannel channel, String recipient) {
        if (recipient == null) {
            return "***";
        }
        if (channel == NotificationChannel.EMAIL && recipient.contains("@")) {
            int atIndex = recipient.indexOf('@');
            return recipient.charAt(0) + "***" + recipient.substring(atIndex);
        }
        if (channel == NotificationChannel.SMS && recipient.length() >= 4) {
            return "***" + recipient.substring(recipient.length() - 4);
        }
        return recipient.length() > 8 ? recipient.substring(0, 8) + "***" : "***";
    }
}
