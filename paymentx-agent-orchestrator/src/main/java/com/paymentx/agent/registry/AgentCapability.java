package com.paymentx.agent.registry;

/**
 * Type-safe tags describing what kind of analysis/retrieval an agent performs. Purely
 * descriptive metadata for discovery/UI purposes (e.g. a future Control Center "AI Agents"
 * listing) - capability values are never checked as a security control themselves; the actual
 * enforcement boundary is {@link AgentDefinition#allowedTools()} via AgentToolPolicy and MCP
 * Gateway's own independent authorization.
 *
 * <p>Kept to the concrete set of investigative domains PaymentX's existing read-only MCP tools
 * and RAG knowledge retrieval already support today, not a speculative taxonomy of every
 * possible future agent - new values should only be added when a real agent needing them is
 * actually being built.
 */
public enum AgentCapability {
    PAYMENT_ANALYSIS,
    ROUTING_ANALYSIS,
    RECONCILIATION_ANALYSIS,
    KNOWLEDGE_RETRIEVAL,
    LOG_ANALYSIS,
    DATABASE_ANALYSIS,
    /** Phase 4.5.3 - conservative, evidence-based potential-risk analysis (never confirmed-fraud
     * determination - PaymentX has no ML fraud model/scoring engine/fraud labels). Added per this
     * enum's own javadoc precedent: "new values should only be added when a real agent needing
     * them is actually being built." */
    RISK_ANALYSIS
}
