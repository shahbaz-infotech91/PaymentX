package com.paymentx.agent.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4.2.2 - a minimal, type-safe RAG retrieval-filter contract, closing the gap the Phase
 * 4.2.0 audit found: RAG Service's own {@code RagQueryRequest.filters} and Vector Service's
 * {@code VectorSearchRequest.filters} already support real, database-layer JSONB-containment
 * metadata filtering end-to-end - only {@link RagServiceClient} never exposed a way to supply
 * one.
 *
 * <p>Fields are limited to exactly the metadata keys the Phase 4.2.1 trusted knowledge corpus
 * (docs/ai/error-analyzer/*.md) actually populates in its document frontmatter - {@code
 * documentType, service, errorCode, severity, retryable} - plus {@code paymentScheme}, which the
 * corpus's own metadata design (Phase 4.2.0 §8) reserves for future scheme-scoped content even
 * though no corpus document is scheme-exclusive today. No speculative field (e.g. a generic
 * {@code tags} list) is included - only what real corpus content already uses or is designed to
 * use, per the instruction not to expose arbitrary raw filter maps or implement every
 * conceivable field.
 *
 * <p>These are retrieval-relevance constraints only, never a security/authorization boundary -
 * they can only narrow which already-ingested, already-non-authoritative knowledge documents are
 * considered for retrieval; RAG Service has no tool-calling capability and no path to any
 * payment-authoritative system, so a filter value can never grant access to anything, and never
 * interacts with {@code AgentToolPolicy}/{@code AgentPlanValidator}/MCP Gateway's own
 * authorization at all.
 *
 * @param documentType e.g. {@code ERROR_CODE_REFERENCE}, {@code OPERATIONAL_REFERENCE}
 * @param service      e.g. {@code paymentx-payment-service}
 * @param paymentScheme one of the three real PaymentX schemes, or {@code null} for no scheme filter
 * @param errorCode    e.g. {@code DUPLICATE_PAYMENT_REFERENCE}
 * @param severity     e.g. {@code HIGH}, {@code MEDIUM}, {@code LOW}
 * @param retryable    filters to documents describing a retryable (or non-retryable) error, when known
 */
public record RagQueryFilters(
        String documentType,
        String service,
        PaymentScheme paymentScheme,
        String errorCode,
        String severity,
        Boolean retryable
) {
    /**
     * Converts to the raw {@code Map<String, Object>} shape RAG Service's own {@code
     * RagQueryRequest.filters} accepts - only non-null fields are included, so an all-null
     * instance produces an empty map (equivalent to "no filters", RAG Service's own default).
     * Every value is a plain String or Boolean, well within {@code RagServiceImpl.validateFilters}'s
     * real constraints (confirmed by direct read: at most 10 filters, each value a simple
     * String/Number/Boolean - never a nested object).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (documentType != null) {
            map.put("documentType", documentType);
        }
        if (service != null) {
            map.put("service", service);
        }
        if (paymentScheme != null) {
            map.put("paymentScheme", paymentScheme.name());
        }
        if (errorCode != null) {
            map.put("errorCode", errorCode);
        }
        if (severity != null) {
            map.put("severity", severity);
        }
        if (retryable != null) {
            map.put("retryable", retryable);
        }
        return map;
    }

    public boolean isEmpty() {
        return toMap().isEmpty();
    }
}
