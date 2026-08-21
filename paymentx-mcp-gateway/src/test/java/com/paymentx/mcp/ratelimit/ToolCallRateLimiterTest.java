package com.paymentx.mcp.ratelimit;

import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Real, non-mocked test against a real Resilience4j RateLimiterRegistry
 * (Step 26/27) - proves the (caller, tool) key really is independent:
 * exhausting one caller/tool's budget never affects a different
 * caller's or a different tool's budget.
 *
 * Hinglish:
 * Ek real Resilience4j RateLimiterRegistry ke against real, non-mocked
 * test (Step 26/27) - prove karta hai ki (caller, tool) key really
 * independent hai: ek caller/tool ka budget exhaust karna kisi doosre
 * caller ya doosre tool ke budget ko kabhi affect nahi karta.
 */
class ToolCallRateLimiterTest {

    private ToolCallRateLimiter newLimiter(int permitsPerPeriod) {
        RateLimiterRegistry registry = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(permitsPerPeriod)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        return new ToolCallRateLimiter(registry);
    }

    @Test
    void checkAndConsume_withinLimit_neverThrows() {
        ToolCallRateLimiter limiter = newLimiter(3);
        assertThatCode(() -> {
            limiter.checkAndConsume("caller-1", "payment.lookup");
            limiter.checkAndConsume("caller-1", "payment.lookup");
            limiter.checkAndConsume("caller-1", "payment.lookup");
        }).doesNotThrowAnyException();
    }

    @Test
    void checkAndConsume_exceedsLimit_throwsToolRateLimited() {
        ToolCallRateLimiter limiter = newLimiter(2);
        limiter.checkAndConsume("caller-1", "payment.lookup");
        limiter.checkAndConsume("caller-1", "payment.lookup");

        assertThatThrownBy(() -> limiter.checkAndConsume("caller-1", "payment.lookup"))
                .isInstanceOf(McpException.class)
                .extracting(ex -> ((McpException) ex).getErrorCode())
                .isEqualTo(McpErrorCodes.TOOL_RATE_LIMITED);
    }

    @Test
    void checkAndConsume_differentTool_hasIndependentBudget() {
        ToolCallRateLimiter limiter = newLimiter(1);
        limiter.checkAndConsume("caller-1", "payment.lookup");

        assertThatCode(() -> limiter.checkAndConsume("caller-1", "routing.lookup")).doesNotThrowAnyException();
    }

    @Test
    void checkAndConsume_differentCaller_hasIndependentBudget() {
        ToolCallRateLimiter limiter = newLimiter(1);
        limiter.checkAndConsume("caller-1", "payment.lookup");

        assertThatCode(() -> limiter.checkAndConsume("caller-2", "payment.lookup")).doesNotThrowAnyException();
    }
}
