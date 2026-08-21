package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.HealthResponse;
import com.paymentx.controlcenter.service.DashboardHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ENGLISH: The dashboard-specific health endpoint the React frontend's
 * status indicator polls. What it does: exposes GET /api/v1/health,
 * returning real, live data from DashboardHealthService wrapped in the
 * standard ApiResponse envelope. Why it exists: distinct from Spring
 * Boot Actuator's generic /actuator/health (also present on this
 * module via spring-boot-starter-actuator) - this is the dashboard's
 * OWN API surface, versioned and shaped exactly how the frontend needs
 * it, rather than the frontend needing to understand Actuator's
 * component-tree response format. How it will communicate with the
 * backend: this IS the backend endpoint - called by the frontend's
 * useHealthCheck hook (src/hooks/useHealthCheck.ts) via
 * src/services/healthService.ts.
 *
 * HINGLISH: Ye dashboard-specific health endpoint hai jise React
 * frontend ka status indicator poll karta hai. Ye kya karti hai: GET
 * /api/v1/health expose karta hai, DashboardHealthService se real, live
 * data ko standard ApiResponse envelope me wrap karke return karta hai.
 * Ye dashboard me kyu hai: Spring Boot Actuator ke generic /actuator/
 * health se alag hai (jo is module par spring-boot-starter-actuator ke
 * through bhi maujood hai) - ye dashboard ka apna API surface hai,
 * versioned aur exactly waise shaped jaisa frontend ko chahiye, na ki
 * frontend ko Actuator ke component-tree response format ko samajhna
 * pade. Backend se kaise connect hogi: yehi backend endpoint hai -
 * frontend ka useHealthCheck hook (src/hooks/useHealthCheck.ts)
 * src/services/healthService.ts ke through ise call karta hai.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final DashboardHealthService dashboardHealthService;

    public HealthController(DashboardHealthService dashboardHealthService) {
        this.dashboardHealthService = dashboardHealthService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(dashboardHealthService.currentHealth());
    }
}
