package com.paymentx.controlcenter.dto.zipkin;

import java.util.Map;

/**
 * ENGLISH: One real span exactly as Zipkin's v2 API returned it - its
 * real trace/span/parent IDs, the real service name that recorded it,
 * its real start timestamp (Zipkin's native microsecond epoch) and
 * real duration in microseconds, and its real tags (which is where
 * this platform's actual correlationId/paymentReference/http.status
 * values live, when a service adds them - never fabricated here).
 *
 * HINGLISH: Bilkul waisa ek real span jaisa Zipkin ke v2 API ne return
 * kiya - iske real trace/span/parent IDs, wo real service name jisne
 * ise record kiya, iska real start timestamp (Zipkin ka native
 * microsecond epoch) aur real duration microseconds me, aur iske real
 * tags (yahi wo jagah hai jahan is platform ke actual
 * correlationId/paymentReference/http.status values rehte hain, jab
 * koi service unhe add karti hai - yahan kabhi fabricate nahi kiye
 * gaye).
 */
public record ZipkinSpan(
        String traceId,
        String spanId,
        String parentId,
        String name,
        String serviceName,
        String kind,
        Long timestampEpochMicros,
        Long durationMicros,
        Map<String, String> tags
) {
}
