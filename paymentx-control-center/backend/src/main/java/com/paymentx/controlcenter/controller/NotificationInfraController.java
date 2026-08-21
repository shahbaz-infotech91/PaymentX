package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.client.MailHogClient;
import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.notification.MailHogStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ENGLISH: The dashboard's real MailHog status endpoint for the Phase
 * 4 Notification page - GET /api/v1/notifications/mailhog returns
 * real reachability, a real captured-message count, and a real link
 * to MailHog's own UI. Named separately from PostgresController's
 * /api/v1/postgres/notifications because this is infrastructure
 * status (an HTTP call to MailHog), not a Postgres query.
 *
 * HINGLISH: Phase 4 Notification page ke liye dashboard ka real
 * MailHog status endpoint - GET /api/v1/notifications/mailhog real
 * reachability, ek real captured-message count, aur MailHog ke apne UI
 * ka ek real link return karta hai. PostgresController ke
 * /api/v1/postgres/notifications se alag naam diya gaya kyunki ye
 * infrastructure status hai (MailHog ko ek HTTP call), Postgres query
 * nahi.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationInfraController {

    private final MailHogClient mailHogClient;

    public NotificationInfraController(MailHogClient mailHogClient) {
        this.mailHogClient = mailHogClient;
    }

    @GetMapping("/mailhog")
    public ApiResponse<MailHogStatus> mailhog() {
        return ApiResponse.success(mailHogClient.status());
    }
}
