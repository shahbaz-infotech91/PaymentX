package com.paymentx.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * English:
 * Every runtime-tunable value this agent reads - the four real
 * downstream AI Platform service URLs (RAG Service, MCP Gateway,
 * Prompt Service, LLM Service), the fixed read-only role set presented
 * to MCP Gateway (see application.yml's class-level comment), and
 * every one of Step 10's bounded execution limits (max-iterations,
 * max-tool-calls, max-tools-per-iteration, max-context-characters,
 * overall-timeout-ms) plus per-dependency connect/read timeouts.
 * Nothing in orchestrator/AgentOrchestratorService, planning/*, or
 * policy/AgentToolPolicy hardcodes any of these numbers - every bound
 * is read from here (Step 10 - "These values must be configurable. Do
 * not hardcode them throughout the code").
 * Why it exists: Step 10's explicit configurability requirement.
 * How it communicates with other components: bound from
 * application.yml's `agent:` block; injected into every client/
 * class, policy/AgentToolPolicy, and orchestrator/AgentOrchestratorService.
 *
 * Hinglish:
 * Har runtime-tunable value jo ye agent padhta hai - char real
 * downstream AI Platform service URLs (RAG Service, MCP Gateway,
 * Prompt Service, LLM Service), MCP Gateway ko present kiya gaya fixed
 * read-only role set (application.yml ka class-level comment dekho),
 * aur Step 10 ki har ek bounded execution limit (max-iterations, max-
 * tool-calls, max-tools-per-iteration, max-context-characters, overall-
 * timeout-ms) plus per-dependency connect/read timeouts.
 * orchestrator/AgentOrchestratorService, planning/*, ya policy/
 * AgentToolPolicy me se koi bhi in numbers me se kisi ko hardcode nahi
 * karta - har bound yahan se padha jaata hai (Step 10 - "Ye values
 * configurable hone chahiye. Poore code me inhe hardcode mat karo").
 * Ye kyu hai: Step 10 ka explicit configurability requirement.
 * Dusre components se kaise communicate karta hai: application.yml ke
 * `agent:` block se bind hota hai; client/ ki har class, policy/
 * AgentToolPolicy, aur orchestrator/AgentOrchestratorService me inject
 * hota hai.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agent")
public class AgentOrchestratorProperties {

    private String ragServiceUrl = "http://localhost:8096";
    private String mcpGatewayUrl = "http://localhost:8097";
    private String promptServiceUrl = "http://localhost:8092";
    private String llmServiceUrl = "http://localhost:8093";
    private String auditServiceUrl = "http://localhost:8085";
    private String agentPromptKey = "PAYMENTX_AGENT_ORCHESTRATOR";

    private int maxIterations = 5;
    private int maxToolCalls = 10;
    private int maxToolsPerIteration = 1;
    private int maxContextCharacters = 12000;
    // Phase 3.9 incremental re-validation fix - see application.yml's matching comment for the real
    // Anthropic-529-overload evidence that discovered both this and ragReadTimeoutMs were too short
    // relative to RAG Service's own real, configured worst-case latency.
    private long overallTimeoutMs = 75000;

    private int ragConnectTimeoutMs = 3000;
    private int ragReadTimeoutMs = 65000;
    private int mcpConnectTimeoutMs = 3000;
    private int mcpReadTimeoutMs = 10000;
    private int promptConnectTimeoutMs = 3000;
    private int promptReadTimeoutMs = 10000;
    private int llmConnectTimeoutMs = 3000;
    private int llmReadTimeoutMs = 60000;

    private List<String> mcpRoles = List.of("PAYMENT_READ", "ROUTING_READ", "RECONCILIATION_READ", "AUDIT_READ");
}
