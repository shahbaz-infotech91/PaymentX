package com.paymentx.vector.dto;

import java.time.OffsetDateTime;

/**
 * English:
 * GET /api/v1/vector/health's real, honest response - Step 31 is
 * explicit that this must verify real database/extension capability
 * ("Where practical verify: PostgreSQL connectivity, pgvector extension
 * availability, required schema") while NOT executing an expensive
 * vector search just to answer a health check - the same
 * cheap-real-signal-not-a-billed/expensive-operation philosophy LLM/
 * Embedding Service's own health endpoints already established for
 * their concern (a paid API call there; an expensive ANN search here).
 * `pgvectorExtensionAvailable` is checked via a real, cheap catalog
 * query (`SELECT 1 FROM pg_extension WHERE extname = 'vector'`), not
 * assumed - see VectorStoreServiceImpl.health()'s javadoc.
 * Why it exists: Step 31's exact health semantics.
 * How it communicates with other components: built by
 * VectorStoreServiceImpl.health(); returned inside
 * ApiResponse&lt;VectorHealthResponse&gt; by VectorController.health.
 *
 * Hinglish:
 * GET /api/v1/vector/health ka real, honest response - Step 31
 * explicit hai ki isse real database/extension capability verify karni
 * chahiye ("Jahan practical ho verify karo: PostgreSQL connectivity,
 * pgvector extension availability, required schema") lekin ek
 * expensive vector search execute kiye bina sirf ek health check ka
 * jawab dene ke liye - wahi cheap-real-signal-not-a-billed/expensive-
 * operation philosophy jo LLM/Embedding Service ke apne health
 * endpoints already apne concern ke liye establish kar chuke hain (wahan
 * ek paid API call; yahan ek expensive ANN search). `pgvectorExtensionAvailable`
 * ek real, cheap catalog query se check hota hai
 * (`SELECT 1 FROM pg_extension WHERE extname = 'vector'`), assume nahi
 * kiya jaata - VectorStoreServiceImpl.health() ka javadoc dekho.
 * Ye kyu hai: Step 31 ke exact health semantics.
 * Dusre components se kaise communicate karta hai:
 * VectorStoreServiceImpl.health() ise banata hai; VectorController.health
 * ise ApiResponse&lt;VectorHealthResponse&gt; ke andar return karta hai.
 */
public record VectorHealthResponse(
        String status,
        boolean databaseReachable,
        boolean pgvectorExtensionAvailable,
        long documentCount,
        OffsetDateTime checkedAt
) {
}
