package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.logs.LogEntry;
import com.paymentx.controlcenter.service.LogFileService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The dashboard's real, bounded, read-only Log Viewer API
 * surface (Phase 4) - GET /api/v1/logs/services lists the fixed,
 * server-configured service allowlist LogFileService reads from; GET
 * /api/v1/logs runs every real filter (service/level/correlationId/
 * traceId/paymentReference/search/time range) against the real,
 * bounded tail of each real service's real log file and returns at
 * most `limit` (server-clamped) entries. There is no endpoint here
 * that streams or loads an entire log file - every response is
 * bounded, matching the Phase 4 brief's explicit "do not load
 * unlimited logs" requirement.
 *
 * HINGLISH: Dashboard ka real, bounded, read-only Log Viewer API
 * surface (Phase 4) - GET /api/v1/logs/services us fixed,
 * server-configured service allowlist ko list karta hai jise
 * LogFileService se padhta hai; GET /api/v1/logs har real filter
 * (service/level/correlationId/traceId/paymentReference/search/time
 * range) ko har real service ki real log file ke real, bounded tail
 * ke against chalata hai aur zyada se zyada `limit` (server-clamped)
 * entries return karta hai. Yahan koi endpoint nahi hai jo poori log
 * file stream ya load kare - har response bounded hai, Phase 4 brief
 * ke explicit "unlimited logs load mat karo" requirement se match
 * karte hue.
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogsController {

    private final LogFileService logFileService;

    public LogsController(LogFileService logFileService) {
        this.logFileService = logFileService;
    }

    @GetMapping("/services")
    public ApiResponse<List<String>> services() {
        return ApiResponse.success(logFileService.knownServices());
    }

    @GetMapping
    public ApiResponse<List<LogEntry>> query(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(
                logFileService.query(service, level, correlationId, traceId, paymentReference, search, from, to, limit));
    }
}
