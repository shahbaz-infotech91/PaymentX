package com.paymentx.controlcenter.dto.zipkin;

import java.util.List;
import java.util.Set;

/**
 * ENGLISH: The dashboard's assembled view of one real trace looked up
 * by Trace ID - its real spans (unmodified), the real distinct set of
 * services that participated, the real overall duration (max span end
 * minus min span start across the real spans - not a value invented
 * separately from them), the real earliest timestamp, and a status
 * derived honestly by scanning every real span's real tags for an
 * "error" tag or a non-2xx/non-SUCCESS outcome - "OK" only when no
 * such signal exists in the real data.
 *
 * HINGLISH: Trace ID se lookup ki gayi ek real trace ka dashboard ka
 * assembled view - iske real spans (unmodified), participate karne
 * wale real distinct services ka set, real overall duration (real
 * spans ke across max span end minus min span start - inse alag se
 * invent kiya gaya value nahi), real earliest timestamp, aur ek status
 * jo har real span ke real tags me "error" tag ya ek non-2xx/
 * non-SUCCESS outcome scan karke honestly derive kiya gaya hai - "OK"
 * sirf tabhi jab real data me aisa koi signal na ho.
 */
public record ZipkinTraceDetail(
        String traceId,
        List<ZipkinSpan> spans,
        Set<String> services,
        Long earliestTimestampEpochMicros,
        Long totalDurationMicros,
        String status
) {
}
