package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.client.ServiceHealthClient;
import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.ServiceHealthStatus;
import com.paymentx.controlcenter.dto.ServiceIdentifier;
import com.paymentx.controlcenter.service.ServiceHealthAggregationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real "Service Health" API surface, covering
 * all 9 real PaymentX business services. What it does: GET
 * /api/v1/services lists the fixed 9-service registry with a real
 * live health probe for each (parallel, real HTTP); GET
 * /api/v1/services/{service}/health|liveness|readiness|info calls the
 * real corresponding Actuator endpoint on the one named service. The
 * {service} path variable is resolved through
 * ServiceIdentifier.fromSlug() - an unknown slug throws
 * IllegalArgumentException (mapped by GlobalExceptionHandler to a 400),
 * so this endpoint can NEVER be used to reach an arbitrary URL; only
 * the 9 allowlisted, server-configured base URLs are ever called. Why
 * it exists: this is the actual Phase 2 Service Health requirement -
 * "expose Dashboard Backend APIs for health/liveness/readiness/build
 * information" - and the SSRF-prevention requirement together in one
 * place. How it will communicate with the backend: called by the
 * frontend's services API client (src/api and src/hooks, Phase 2
 * frontend wiring).
 *
 * HINGLISH: Dashboard ka real "Service Health" API surface, jo saare 9
 * real PaymentX business services ko cover karta hai. Ye kya karti hai:
 * GET /api/v1/services fixed 9-service registry list karta hai jisme
 * har ek ke liye ek real live health probe hota hai (parallel, real
 * HTTP); GET /api/v1/services/{service}/health|liveness|readiness|info
 * ek naamed service ke real corresponding Actuator endpoint ko call
 * karta hai. {service} path variable ko
 * ServiceIdentifier.fromSlug() ke through resolve kiya jata hai - ek
 * unknown slug IllegalArgumentException throw karta hai
 * (GlobalExceptionHandler dwara 400 me map kiya gaya), isliye is
 * endpoint ka use kabhi bhi arbitrary URL tak pahunchne ke liye nahi ho
 * sakta; sirf 9 allowlisted, server-configured base URLs hi kabhi call
 * hote hain. Ye dashboard me kyu hai: yehi actual Phase 2 Service
 * Health requirement hai - "health/liveness/readiness/build
 * information ke liye Dashboard Backend APIs expose karo" - aur
 * SSRF-prevention requirement dono ek hi jagah. Backend se kaise
 * connect hogi: frontend ka services API client (src/api aur
 * src/hooks, Phase 2 frontend wiring) ise call karta hai.
 */
@RestController
@RequestMapping("/api/v1/services")
public class ServicesController {

    private final ServiceHealthAggregationService aggregationService;

    public ServicesController(ServiceHealthAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @GetMapping
    public ApiResponse<List<ServiceHealthStatus>> listServicesWithHealth() {
        return ApiResponse.success(aggregationService.checkAll());
    }

    @GetMapping("/{service}/health")
    public ApiResponse<ServiceHealthStatus> health(@PathVariable String service) {
        ServiceIdentifier identifier = ServiceIdentifier.fromSlug(service);
        return ApiResponse.success(aggregationService.checkOne(identifier, ServiceHealthClient.Probe.HEALTH));
    }

    @GetMapping("/{service}/liveness")
    public ApiResponse<ServiceHealthStatus> liveness(@PathVariable String service) {
        ServiceIdentifier identifier = ServiceIdentifier.fromSlug(service);
        return ApiResponse.success(aggregationService.checkOne(identifier, ServiceHealthClient.Probe.LIVENESS));
    }

    @GetMapping("/{service}/readiness")
    public ApiResponse<ServiceHealthStatus> readiness(@PathVariable String service) {
        ServiceIdentifier identifier = ServiceIdentifier.fromSlug(service);
        return ApiResponse.success(aggregationService.checkOne(identifier, ServiceHealthClient.Probe.READINESS));
    }

    @GetMapping("/{service}/info")
    public ApiResponse<ServiceHealthStatus> info(@PathVariable String service) {
        ServiceIdentifier identifier = ServiceIdentifier.fromSlug(service);
        return ApiResponse.success(aggregationService.checkOne(identifier, ServiceHealthClient.Probe.INFO));
    }
}
