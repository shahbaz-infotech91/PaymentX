package com.paymentx.notification.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.PageResponse;
import com.paymentx.notification.dto.DeliveryAttemptResponse;
import com.paymentx.notification.dto.NotificationResponse;
import com.paymentx.notification.dto.NotificationSearchCriteria;
import com.paymentx.notification.dto.ResendNotificationRequest;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * WHY retry/resend require NOTIFICATION_ADMIN (@PreAuthorize), matching
 * Routing/Audit Service's mutating-endpoint pattern: the explicit
 * "Admin-only resend APIs" requirement - re-triggering delivery is an
 * operator action, not something any authenticated caller should be
 * able to invoke freely.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification search, history, and admin-triggered retry/resend")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationController is a REST controller in the notification module of PaymentX. It lives in package com.paymentx.notification.controller and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationController PaymentX ke notification module ka ek REST controller hai. Ye com.paymentx.notification.controller package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a notification by id")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Search notifications", description = "All parameters are optional and combined with AND.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> search(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) SourceEventType sourceEventType,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String participantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        NotificationSearchCriteria criteria = new NotificationSearchCriteria(
                status, channel, sourceEventType, recipient, participantId, fromDate, toDate);

        return ResponseEntity.ok(ApiResponse.success(notificationService.search(criteria, page, size)));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get a notification's full delivery-attempt history")
    public ResponseEntity<ApiResponse<List<DeliveryAttemptResponse>>> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getHistory(id)));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('NOTIFICATION_ADMIN')")
    @Operation(summary = "Retry a failed/dead-lettered notification (admin only)")
    public ResponseEntity<ApiResponse<NotificationResponse>> retry(
            @PathVariable UUID id, @Valid @RequestBody ResendNotificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.retry(id, request)));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasRole('NOTIFICATION_ADMIN')")
    @Operation(summary = "Resend a notification as a new, separately-tracked send (admin only)")
    public ResponseEntity<ApiResponse<NotificationResponse>> resend(
            @PathVariable UUID id, @Valid @RequestBody ResendNotificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.resend(id, request)));
    }
}
