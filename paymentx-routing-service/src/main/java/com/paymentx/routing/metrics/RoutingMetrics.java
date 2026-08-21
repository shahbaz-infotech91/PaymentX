package com.paymentx.routing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * WHY hand-registered meters rather than @Timed/@Counted annotations:
 * annotation-based metrics require Spring AOP proxying and only capture
 * whole-method timing - cache-hit vs cache-miss latency (the actually
 * interesting distinction for a routing lookup) can't be expressed that
 * way, since both paths go through the same public method. Explicit
 * MeterRegistry calls let resolveRoute() tag the SAME timer differently
 * depending on which path was taken.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingMetrics is a component in the routing module of PaymentX. It lives in package com.paymentx.routing.metrics and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingMetrics PaymentX ke routing module ka ek component hai. Ye com.paymentx.routing.metrics package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingMetrics {

    private static final String ROUTE_LOOKUP_TIMER = "routing.route.lookup";
    private static final String CACHE_HIT_COUNTER = "routing.cache.hit";
    private static final String CACHE_MISS_COUNTER = "routing.cache.miss";
    private static final String RULE_CHANGE_COUNTER = "routing.rule.change";
    private static final String ACTIVE_ROUTES_GAUGE = "routing.rules.active.count";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeRouteCount = new AtomicInteger(0);

    public RoutingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder(ACTIVE_ROUTES_GAUGE, activeRouteCount, AtomicInteger::get)
                .description("Number of currently active routing rules, refreshed on every create/update/delete")
                .register(meterRegistry);
    }

    /** Called after any write that changes the active-rule count (see
     *  RoutingServiceImpl) - a Gauge reads its value lazily on scrape, so
     *  this just updates the backing AtomicInteger Micrometer already
     *  holds a reference to. */
    public void setActiveRouteCount(long count) {
        activeRouteCount.set((int) count);
    }

    public Timer.Sample startRouteLookupTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordRouteLookup(Timer.Sample sample, String scheme, boolean cacheHit) {
        sample.stop(Timer.builder(ROUTE_LOOKUP_TIMER)
                .tag("scheme", scheme)
                .tag("cacheHit", String.valueOf(cacheHit))
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    public void recordCacheHit(String scheme) {
        Counter.builder(CACHE_HIT_COUNTER).tag("scheme", scheme).register(meterRegistry).increment();
    }

    public void recordCacheMiss(String scheme) {
        Counter.builder(CACHE_MISS_COUNTER).tag("scheme", scheme).register(meterRegistry).increment();
    }

    public void recordRuleChange(String changeType) {
        Counter.builder(RULE_CHANGE_COUNTER).tag("changeType", changeType).register(meterRegistry).increment();
    }
}
