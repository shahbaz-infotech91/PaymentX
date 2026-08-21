package com.paymentx.mcp.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * English:
 * One shared Resilience4j RateLimiterRegistry, configured from
 * McpGatewayProperties' rate-limit-permits-per-period/rate-limit-
 * period-seconds (default 30 tool calls per 60s window). Deliberately a
 * REGISTRY, not one fixed RateLimiter instance - ratelimit/
 * ToolCallRateLimiter creates one real, independent RateLimiter per
 * (callerId, toolName) key on first use (registry.rateLimiter(key,
 * baseConfig)), so one caller hammering payment.lookup cannot exhaust
 * another caller's or another tool's budget (Step 26 - "per user, per
 * client, per tool"). rate-limit-timeout-millis defaults to 0 - a
 * request that cannot acquire a permit immediately is rejected
 * (TOOL_RATE_LIMITED) rather than queued/blocked, matching Step 23's
 * "never allow an AI tool invocation to hang indefinitely."
 * Why it exists: Step 26/27 - "MCP must protect PaymentX services...
 * prevent an AI agent from generating unlimited tool calls."
 * How it communicates with other components: injected into
 * ratelimit/ToolCallRateLimiter, the only class that ever calls
 * registry.rateLimiter(...).
 *
 * Hinglish:
 * Ek shared Resilience4j RateLimiterRegistry, McpGatewayProperties ke
 * rate-limit-permits-per-period/rate-limit-period-seconds se configure
 * kiya gaya (default 30 tool calls per 60s window). Jaan-boojh kar ek
 * REGISTRY hai, ek fixed RateLimiter instance nahi - ratelimit/
 * ToolCallRateLimiter har (callerId, toolName) key ke liye pehli use
 * par ek real, independent RateLimiter banata hai (registry.rateLimiter(key,
 * baseConfig)), taaki ek caller jo payment.lookup ko hammer kare kisi
 * doosre caller ya doosre tool ka budget exhaust na kar sake (Step 26 -
 * "per user, per client, per tool"). rate-limit-timeout-millis default
 * 0 hai - ek request jo turant permit acquire nahi kar sakti reject ho
 * jaati hai (TOOL_RATE_LIMITED), queue/block nahi hoti, Step 23 ke "ek
 * AI tool invocation ko kabhi indefinitely hang hone ki ijazat mat do"
 * se match karte hue.
 * Ye kyu hai: Step 26/27 - "MCP ko PaymentX services protect karni
 * chahiye... ek AI agent ko unlimited tool calls generate karne se
 * roko."
 * Dusre components se kaise communicate karta hai: ratelimit/
 * ToolCallRateLimiter me inject hota hai, jo ki registry.rateLimiter(...)
 * ko kabhi call karne wali ek hi class hai.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(McpGatewayProperties properties) {
        io.github.resilience4j.ratelimiter.RateLimiterConfig baseConfig = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(properties.getRateLimitPermitsPerPeriod())
                .limitRefreshPeriod(Duration.ofSeconds(properties.getRateLimitPeriodSeconds()))
                .timeoutDuration(Duration.ofMillis(properties.getRateLimitTimeoutMillis()))
                .build();
        return RateLimiterRegistry.of(baseConfig);
    }
}
