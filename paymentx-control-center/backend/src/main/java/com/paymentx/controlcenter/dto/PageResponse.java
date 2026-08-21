package com.paymentx.controlcenter.dto;

import java.util.List;

/**
 * ENGLISH: The one pagination shape every Postgres read-only endpoint
 * returns. What it does: wraps a page of real rows together with the
 * real page/size that was applied and the real total row count for
 * that query (a genuine COUNT(*), not an estimate) - the frontend
 * needs this to render page controls. Why it exists: the Phase 2 brief
 * explicitly requires "pagination for large datasets" on every
 * Postgres listing endpoint; this is the one shared shape so every
 * repository/controller pair doesn't invent its own. How it will
 * communicate with the backend: returned (wrapped in ApiResponse) by
 * every list endpoint in controller/postgres/*.
 *
 * HINGLISH: Har Postgres read-only endpoint jo ek hi pagination shape
 * return karta hai. Ye kya karti hai: real rows ke ek page ko us real
 * page/size ke saath wrap karta hai jo apply hua tha aur us query ke
 * liye real total row count (ek genuine COUNT(*), estimate nahi) - is
 * ki zarurat frontend ko page controls render karne ke liye hoti hai.
 * Ye dashboard me kyu hai: Phase 2 brief explicitly har Postgres
 * listing endpoint par "pagination for large datasets" maangta hai; ye
 * ek shared shape hai taaki har repository/controller pair apni khud
 * ki na banaye. Backend se kaise connect hogi: controller/postgres/
 * ka har list endpoint ise (ApiResponse me wrapped) return karta hai.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {
}
