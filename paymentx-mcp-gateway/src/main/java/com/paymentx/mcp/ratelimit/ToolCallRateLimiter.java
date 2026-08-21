package com.paymentx.mcp.ratelimit;

import com.paymentx.mcp.exception.McpException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * English:
 * Step 26/27's real enforcement point - "prevent an AI agent from
 * generating unlimited tool calls," keyed by (caller, tool) so one
 * caller/tool pair being hammered cannot exhaust another's budget.
 * Reuses config/RateLimiterConfig's shared RateLimiterRegistry (real
 * Resilience4j RateLimiter, not a hand-rolled counter) - registry.rateLimiter(key,
 * baseConfig) lazily creates one real, independent RateLimiter per key
 * on first use and returns the same instance on every subsequent call
 * for that key, which is exactly the "per user, per client, per tool"
 * requirement. acquirePermission() with the configured (default zero)
 * timeout returns immediately true/false rather than blocking - a
 * denied call throws TOOL_RATE_LIMITED at once, matching Step 23's
 * "never allow an AI tool invocation to hang indefinitely."
 * Why it exists: Step 26/27.
 * How it communicates with other components: called by ToolInvoker
 * immediately after authorization passes and before execute() runs.
 *
 * Hinglish:
 * Step 26/27 ka real enforcement point - "ek AI agent ko unlimited tool
 * calls generate karne se roko," (caller, tool) se keyed taaki ek
 * caller/tool pair ko hammer karna kisi doosre ka budget exhaust na
 * kar sake. config/RateLimiterConfig ka shared RateLimiterRegistry
 * reuse karta hai (real Resilience4j RateLimiter, ek hand-rolled
 * counter nahi) - registry.rateLimiter(key, baseConfig) pehli use par
 * lazily ek real, independent RateLimiter banata hai har key ke liye
 * aur us key ke liye har baad ki call par wahi instance return karta
 * hai, jo exactly "per user, per client, per tool" requirement hai.
 * acquirePermission() configured (default zero) timeout ke saath turant
 * true/false return karta hai, block nahi karta - ek denied call turant
 * TOOL_RATE_LIMITED throw karta hai, Step 23 ke "ek AI tool invocation
 * ko kabhi indefinitely hang hone ki ijazat mat do" se match karte hue.
 * Ye kyu hai: Step 26/27.
 * Dusre components se kaise communicate karta hai: ToolInvoker dwara
 * authorization pass hone ke turant baad aur execute() chalne se pehle
 * call hota hai.
 */
@Component
@Slf4j
public class ToolCallRateLimiter {

    private final RateLimiterRegistry rateLimiterRegistry;

    public ToolCallRateLimiter(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    public void checkAndConsume(String callerId, String toolName) {
        String key = (callerId == null || callerId.isBlank() ? "anonymous" : callerId) + "::" + toolName;
        RateLimiter limiter = rateLimiterRegistry.rateLimiter(key);
        boolean permitted = limiter.acquirePermission();
        if (!permitted) {
            log.warn("Rate limit exceeded key={}", key);
            throw McpException.rateLimited("Rate limit exceeded for tool: " + toolName);
        }
    }
}
