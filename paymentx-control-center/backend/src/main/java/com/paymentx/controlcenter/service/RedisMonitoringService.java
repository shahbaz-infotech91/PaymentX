package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.RedisMonitoringClient;
import com.paymentx.controlcenter.dto.redis.RedisHealthStatus;
import com.paymentx.controlcenter.dto.redis.RedisKeySample;
import com.paymentx.controlcenter.dto.redis.RedisKeyspaceStats;
import com.paymentx.controlcenter.dto.redis.RedisMemoryInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ENGLISH: Thin pass-through service layer between RedisController and
 * RedisMonitoringClient - kept as its own class purely for the same
 * controller/service/client layering every other domain in this
 * backend follows.
 *
 * HINGLISH: RedisController aur RedisMonitoringClient ke beech ek
 * patla pass-through service layer - isi controller/service/client
 * layering ke liye apni khud ki class rakhi gayi hai jo is backend ka
 * har doosra domain follow karta hai.
 */
@Service
public class RedisMonitoringService {

    private final RedisMonitoringClient client;

    public RedisMonitoringService(RedisMonitoringClient client) {
        this.client = client;
    }

    public RedisHealthStatus health() {
        return client.health();
    }

    public RedisMemoryInfo memoryInfo() {
        return client.memoryInfo();
    }

    public RedisKeyspaceStats keyspaceStats() {
        return client.keyspaceStats();
    }

    public List<RedisKeySample> sampleKeys() {
        return client.sampleKeys();
    }
}
