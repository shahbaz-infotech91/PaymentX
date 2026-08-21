package com.paymentx.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * The async report-generation entry point - matches
 * ReconciliationBatchProcessor/NotificationDispatcher's exact
 * @Async-lives-on-a-separate-bean pattern (Spring's @Async proxying
 * only intercepts calls arriving THROUGH the proxy - a method calling
 * another @Async method on `this` bypasses the proxy and runs
 * synchronously, so this MUST be a separate injected bean).
 *
 * The actual transactional work lives in ReportGenerationRunner, a
 * SEPARATE bean again (not just a private method here) - for the exact
 * same self-invocation reason applied one level deeper: calling
 * run() on `this` from generateAsync() would bypass run()'s
 * @Transactional(REQUIRES_NEW) advice too, since both would then be
 * same-instance calls on this one bean.
 *
 * HINGLISH:
 * Async report-generation entry point - ReconciliationBatchProcessor/
 * NotificationDispatcher ke exact @Async-alag-bean-me-rehta-hai pattern
 * jaisa (Spring ka @Async proxying sirf un calls ko intercept karta hai
 * jo PROXY ke through aati hain - isliye ye ALAG injected bean hona
 * zaroori hai).
 *
 * Actual transactional kaam ReportGenerationRunner me hai, jo khud bhi
 * ek ALAG bean hai (sirf private method nahi) - wahi self-invocation
 * reason ek level neeche bhi apply hota hai: agar generateAsync() se
 * run() ko `this` pe call karte to run() ka @Transactional(REQUIRES_NEW)
 * advice bhi bypass ho jaata, kyunki dono hi is ek bean pe same-instance
 * calls ban jaate.
 * ====================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationProcessor {

    private final ReportGenerationRunner reportGenerationRunner;

    @Async("reportGenerationExecutor")
    public void generateAsync(UUID executionId) {
        reportGenerationRunner.run(executionId);
    }
}
