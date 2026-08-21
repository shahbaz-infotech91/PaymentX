package com.paymentx.controlcenter.security;

/**
 * ENGLISH: Still intentionally NOT a Spring Security configuration
 * class - this module has no spring-boot-starter-security dependency.
 * What it does: nothing at runtime; it remains a documented placeholder
 * for the required security/ package. Why it exists (updated, Phase 6):
 * adding the full security starter would still be dishonest scaffolding
 * for the same reason recorded here since Phase 1 - Auth Service has
 * zero endpoints today (confirmed live again during Phase 5/6 work), so
 * there is still no real per-user IdP to authenticate against. Phase 6
 * added real, working authentication anyway, just not via this class or
 * this starter: see config/DashboardAuthFilter.java + Control
 * CenterProperties.Security - a lightweight, explicit, opt-in
 * (control-center.security.enabled) shared-dashboard-token filter that
 * protects this backend's own API from unauthenticated network access,
 * without pretending to be multi-user authorization it cannot honestly
 * provide. How it will communicate with the backend: N/A here - see
 * DashboardAuthFilter for the real request-gating logic.
 *
 * HINGLISH: Ab bhi jaan-bujhkar ek Spring Security configuration class
 * NAHI hai - is module me spring-boot-starter-security dependency nahi
 * hai. Ye kya karti hai: runtime me kuch nahi; required security/
 * package ke liye ek documented placeholder bani rehti hai. Ye
 * dashboard me kyu hai (updated, Phase 6): pura security starter add
 * karna ab bhi usi reason se dishonest scaffolding hoga jo Phase 1 se
 * yahan record hai - Auth Service ke aaj zero endpoints hain (Phase
 * 5/6 work ke dauraan dobara live confirm kiya gaya), isliye ab bhi
 * authenticate karne ke liye koi real per-user IdP nahi hai. Phase 6 ne
 * phir bhi real, working authentication add kiya, bas is class ya is
 * starter ke through nahi: config/DashboardAuthFilter.java + Control
 * CenterProperties.Security dekho - ek lightweight, explicit, opt-in
 * (control-center.security.enabled) shared-dashboard-token filter jo
 * is backend ke apne API ko unauthenticated network access se protect
 * karta hai, bina wo multi-user authorization hone ka dikhava kiye jo
 * ye honestly provide nahi kar sakta. Backend se kaise connect hogi:
 * yahan N/A - real request-gating logic ke liye DashboardAuthFilter
 * dekho.
 */
public final class SecurityConfig {
    private SecurityConfig() {}
}
