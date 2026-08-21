package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.redis.RedisHealthStatus;
import com.paymentx.controlcenter.dto.redis.RedisKeySample;
import com.paymentx.controlcenter.dto.redis.RedisKeyspaceStats;
import com.paymentx.controlcenter.dto.redis.RedisMemoryInfo;
import com.paymentx.controlcenter.service.RedisMonitoringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real, read-only Redis monitoring API
 * surface - connection/health, memory, and keyspace population by
 * real category. No endpoint here ever returns a key's value or
 * accepts a key pattern from the caller.
 *
 * HINGLISH: Dashboard ka real, read-only Redis monitoring API surface
 * - connection/health, memory, aur real category ke hisaab se keyspace
 * population. Yahan koi endpoint kabhi kisi key ki value return nahi
 * karta ya caller se koi key pattern accept nahi karta.
 */
@RestController
@RequestMapping("/api/v1/redis")
public class RedisController {

    private final RedisMonitoringService service;

    public RedisController(RedisMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ApiResponse<RedisHealthStatus> health() {
        return ApiResponse.success(service.health());
    }

    @GetMapping("/memory")
    public ApiResponse<RedisMemoryInfo> memory() {
        return ApiResponse.success(service.memoryInfo());
    }

    @GetMapping("/keyspace")
    public ApiResponse<RedisKeyspaceStats> keyspace() {
        return ApiResponse.success(service.keyspaceStats());
    }

    @GetMapping("/samples")
    public ApiResponse<List<RedisKeySample>> samples() {
        return ApiResponse.success(service.sampleKeys());
    }
}
