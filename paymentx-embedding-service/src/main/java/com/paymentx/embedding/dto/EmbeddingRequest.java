package com.paymentx.embedding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * English:
 * The body of POST /api/v1/embeddings - a single piece of text to
 * convert into a vector. `model` is an optional per-request override of
 * this service's configured default (embedding.openai.model) - null
 * means "use the configured default", never a silently-different
 * hardcoded value (matches LLM Service's GenerateRequest.model
 * convention exactly). `text` is bounded by @Size here as basic input
 * hygiene at the API boundary; the real, configured
 * embedding.max-input-length is enforced by EmbeddingServiceImpl
 * against the live config value (not this fixed annotation ceiling),
 * so an operator can tune the real limit without a code change - this
 * annotation only prevents a pathologically large payload from ever
 * reaching deserialization-adjacent code.
 * Why it exists: Step 6 of the Phase 3.4 brief's exact contract shape.
 * How it communicates with other components: bound by
 * EmbeddingController.embed; mapped by EmbeddingServiceImpl into a
 * provider-agnostic EmbeddingProviderRequest.
 *
 * Hinglish:
 * POST /api/v1/embeddings ka body - ek text jise vector me convert
 * karna hai. `model` is service ke configured default
 * (embedding.openai.model) ka ek optional per-request override hai -
 * null ka matlab hai "configured default use karo", kabhi ek silently-
 * different hardcoded value nahi (LLM Service ke GenerateRequest.model
 * convention se exactly match karta hai). `text` yahan @Size se bounded
 * hai basic input hygiene ke roop me API boundary par; real, configured
 * embedding.max-input-length EmbeddingServiceImpl dwara live config
 * value ke against enforce hota hai (is fixed annotation ceiling ke
 * against nahi), taaki ek operator bina code change ke real limit tune
 * kar sake - ye annotation sirf ek pathologically large payload ko
 * deserialization-adjacent code tak pahunchne se rokta hai.
 * Ye kyu hai: Phase 3.4 brief ke Step 6 ka exact contract shape.
 * Dusre components se kaise communicate karta hai:
 * EmbeddingController.embed ise bind karta hai; EmbeddingServiceImpl
 * ise ek provider-agnostic EmbeddingProviderRequest me map karta hai.
 */
public record EmbeddingRequest(

        @NotBlank(message = "text must not be blank")
        @Size(max = 20_000, message = "text must be at most 20,000 characters")
        String text,

        @Size(max = 200, message = "model must be at most 200 characters")
        String model
) {
}
