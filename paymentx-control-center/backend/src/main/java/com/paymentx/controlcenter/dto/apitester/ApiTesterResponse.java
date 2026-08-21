package com.paymentx.controlcenter.dto.apitester;

import java.util.Map;

/**
 * ENGLISH: The real, unmodified response one allowlisted PaymentX
 * endpoint returned - real HTTP status, real response headers
 * (flattened to one value per name), real body text exactly as
 * received (never reformatted or filtered - if the real endpoint
 * returned something sensitive, that is that endpoint's own real
 * behavior, not something this DTO should silently hide), and a real
 * responseTimeMillis measured around the actual outbound call.
 *
 * HINGLISH: Ek allowlisted PaymentX endpoint ne jo real, unmodified
 * response return kiya - real HTTP status, real response headers (har
 * naam ke liye ek value tak flatten kiye gaye), real body text bilkul
 * waisa jaisa receive hua (kabhi reformat ya filter nahi kiya gaya -
 * agar real endpoint ne kuch sensitive return kiya toh ye us endpoint
 * ka apna real behavior hai, aisi cheez nahi jise ye DTO silently
 * chhupaye), aur ek real responseTimeMillis jo actual outbound call ke
 * around measure kiya gaya.
 */
public record ApiTesterResponse(
        int status,
        String statusText,
        Map<String, String> headers,
        String body,
        long responseTimeMillis
) {
}
