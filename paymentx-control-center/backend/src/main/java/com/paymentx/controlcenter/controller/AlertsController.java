package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.alerts.Alert;
import com.paymentx.controlcenter.service.AlertsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real operational alerts API - GET
 * /api/v1/alerts evaluates all 9 real conditions fresh on every call
 * and returns exactly what is currently true, nothing more. An empty
 * array means the platform is genuinely healthy right now, not that
 * this endpoint is broken.
 *
 * HINGLISH: Dashboard ka real operational alerts API - GET
 * /api/v1/alerts har call par saare 9 real conditions fresh evaluate
 * karta hai aur exactly wahi return karta hai jo abhi true hai, aur
 * kuch nahi. Ek empty array ka matlab hai platform abhi genuinely
 * healthy hai, ye nahi ki ye endpoint broken hai.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertsController {

    private final AlertsService service;

    public AlertsController(AlertsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Alert>> alerts() {
        return ApiResponse.success(service.currentAlerts());
    }
}
