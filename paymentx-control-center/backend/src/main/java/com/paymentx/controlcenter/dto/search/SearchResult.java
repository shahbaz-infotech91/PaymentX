package com.paymentx.controlcenter.dto.search;

/**
 * ENGLISH: One real Global Search hit - a real row's identifying
 * fields (title/subtitle built from real columns, never invented) and
 * a real key the frontend uses to navigate (a payment_reference, a
 * bank_id, or a settlement file id) - the frontend, not this backend,
 * decides the exact route to build from (type, key), keeping routing
 * decisions out of the API contract.
 *
 * HINGLISH: Ek real Global Search hit - ek real row ke identifying
 * fields (title/subtitle real columns se bane, kabhi invent nahi kiye
 * gaye) aur ek real key jise frontend navigate karne ke liye use karta
 * hai (ek payment_reference, ek bank_id, ya ek settlement file id) -
 * frontend, ye backend nahi, (type, key) se exact route banane ka
 * faisla karta hai, routing decisions ko API contract se bahar rakhte
 * hue.
 */
public record SearchResult(
        SearchResultType type,
        String key,
        String title,
        String subtitle
) {
}
