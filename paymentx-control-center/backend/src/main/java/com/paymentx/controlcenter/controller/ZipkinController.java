package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.client.ZipkinClient;
import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.zipkin.ZipkinTraceDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real Zipkin API surface - real known
 * services (GET /api/v1/zipkin/services) and real trace lookup by
 * Trace ID (GET /api/v1/zipkin/traces/{traceId}), returning real
 * spans/duration/timestamp/status assembled by ZipkinClient. A
 * traceId with no spans is a genuine ControlCenterException (mapped
 * to 502 by GlobalExceptionHandler), never a fabricated empty trace.
 *
 * HINGLISH: Dashboard ka real Zipkin API surface - real known services
 * (GET /api/v1/zipkin/services) aur Trace ID se real trace lookup
 * (GET /api/v1/zipkin/traces/{traceId}), ZipkinClient dwara assemble
 * kiye gaye real spans/duration/timestamp/status return karte hue. Ek
 * traceId jiske spans nahi hain wo ek genuine ControlCenterException
 * hai (GlobalExceptionHandler dwara 502 me map kiya gaya), kabhi ek
 * fabricated empty trace nahi.
 */
@RestController
@RequestMapping("/api/v1/zipkin")
public class ZipkinController {

    private final ZipkinClient client;

    public ZipkinController(ZipkinClient client) {
        this.client = client;
    }

    @GetMapping("/services")
    public ApiResponse<List<String>> services() {
        return ApiResponse.success(client.services());
    }

    @GetMapping("/traces/{traceId}")
    public ApiResponse<ZipkinTraceDetail> trace(@PathVariable String traceId) {
        return ApiResponse.success(client.trace(traceId));
    }
}
