package com.paymentx.controlcenter.dto.apitester;

import java.util.Map;

/**
 * ENGLISH: What the browser is allowed to submit to the API Tester -
 * deliberately NOT a URL. service is a ServiceIdentifier slug and path
 * is a concrete path (e.g. "/api/v1/payments/ABC123"); ApiTesterService
 * resolves service+method+path against the real, fixed allowlist
 * before ever constructing an outbound request - there is no field
 * here (and never will be) that lets a caller specify a host, port, or
 * scheme. headers/body are the user's own real request content (e.g. a
 * real Authorization/X-Api-Key they already hold) - this backend never
 * injects its own secrets into them.
 *
 * HINGLISH: Browser API Tester ko kya submit karne ki ijazat rakhta hai
 * - jaan-boojh kar ek URL NAHI. service ek ServiceIdentifier slug hai
 * aur path ek concrete path hai (jaise "/api/v1/payments/ABC123");
 * ApiTesterService kisi bhi outbound request banane se pehle
 * service+method+path ko real, fixed allowlist ke against resolve
 * karta hai - yahan koi field nahi hai (aur kabhi nahi hogi) jo caller
 * ko host, port, ya scheme specify karne de. headers/body user ka apna
 * real request content hai (jaise ek real Authorization/X-Api-Key jo
 * unke paas already hai) - ye backend kabhi apne khud ke secrets unme
 * inject nahi karta.
 */
public record ApiTesterRequest(
        String service,
        String method,
        String path,
        Map<String, String> queryParams,
        Map<String, String> headers,
        String body
) {
}
