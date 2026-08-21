package com.paymentx.controlcenter.dto.apitester;

/**
 * ENGLISH: One real, allowlisted PaymentX endpoint the Phase 5 API
 * Tester is permitted to call - service is a real ServiceIdentifier
 * slug (never a raw host/URL), method/pathTemplate are copied straight
 * from that service's real @RequestMapping/@GetMapping/etc.
 * annotations (verified live against the actual controller source,
 * not guessed), and destructive is true only for the two real DELETE
 * endpoints this platform exposes - both surfaced here deliberately
 * ("DELETE only where explicitly allowed"), never silently.
 *
 * HINGLISH: Ek real, allowlisted PaymentX endpoint jise Phase 5 API
 * Tester call karne ki ijazat rakhta hai - service ek real
 * ServiceIdentifier slug hai (kabhi ek raw host/URL nahi),
 * method/pathTemplate seedhe us service ke real
 * @RequestMapping/@GetMapping/etc. annotations se copy kiye gaye hain
 * (actual controller source ke against live verify kiye gaye, guess
 * nahi kiye gaye), aur destructive sirf un do real DELETE endpoints ke
 * liye true hai jo ye platform expose karta hai - dono yahan
 * jaan-boojh kar surface kiye gaye hain ("DELETE sirf jahan explicitly
 * allowed ho"), kabhi silently nahi.
 */
public record ApiTesterEndpointDescriptor(
        String service,
        String method,
        String pathTemplate,
        String description,
        boolean destructive
) {
}
