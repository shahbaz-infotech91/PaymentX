package com.paymentx.controlcenter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.controlcenter.config.CorrelationIdFilter;
import com.paymentx.controlcenter.dto.ai.AiComponentStatus;
import com.paymentx.controlcenter.exception.AiServiceNotReadyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * ENGLISH: The real HTTP client that calls the real AI Platform backend
 * services (Prompt Service, Phase 3.2; LLM Service, Phase 3.3; RAG
 * Service, Phase 3.6; Agent Orchestrator, Phase 3.8) - matches ServiceHealthClient's exact
 * JsonNode-parsing style (this
 * module deliberately has no compile-time dependency on
 * paymentx-llm-service's/paymentx-prompt-service's own DTO classes -
 * every other client/*Client.java in this package makes the same
 * choice, so a monitoring dashboard never needs to be rebuilt just
 * because a business service's internal DTO shape changes). What it
 * does: generate() POSTs an already-typed prompt to LLM Service's real
 * /api/v1/llm/generate and returns its real content/refused/stopReason
 * - or throws AiServiceNotReadyException carrying the REAL upstream
 * errorCode/message (LLM_NOT_CONFIGURED, LLM_RATE_LIMITED, etc. - see
 * paymentx-llm-service's LlmErrorCodes) rather than a generic one, so
 * the frontend can tell "the provider has no credentials configured"
 * apart from "the provider is rate-limited" apart from "LLM Service is
 * unreachable." componentHealth() probes a real /actuator/health on
 * either service (free, unauthenticated, no billed provider call - the
 * same honesty constraint LLM Service's own GET /api/v1/llm/health
 * applies to itself) and maps UP -> READY / anything else -> NOT_READY,
 * so AiHealthResponse.components can finally report something other
 * than a hardcoded NOT_IMPLEMENTED for promptService/llmService now
 * that both real services exist. Why it exists: Step 10 of the Phase
 * 3.3 brief. How it will communicate with the backend: called by
 * AiChatService; only ever targets ControlCenterProperties.Ai's
 * server-configured URLs, never a browser-supplied one (same SSRF
 * boundary ServiceHealthClient's own javadoc documents for the 9
 * business services).
 *
 * HINGLISH: Do real AI Platform backend services (Prompt Service, Phase
 * 3.2; LLM Service, Phase 3.3) ko call karne wala real HTTP client -
 * ServiceHealthClient ke exact JsonNode-parsing style se match karta
 * hai (is module ka jaan-boojh kar paymentx-llm-service/paymentx-
 * prompt-service ke apne DTO classes par koi compile-time dependency
 * nahi hai - is package ka har doosra client/*Client.java yahi choice
 * karta hai, taaki ek monitoring dashboard ko sirf isliye rebuild na
 * karna pade kyunki ek business service ka internal DTO shape badal
 * gaya). Ye kya karti hai: generate() ek already-typed prompt ko LLM
 * Service ke real /api/v1/llm/generate par POST karta hai aur uska real
 * content/refused/stopReason return karta hai - ya AiServiceNotReadyException
 * throw karta hai jo REAL upstream errorCode/message carry karta hai
 * (LLM_NOT_CONFIGURED, LLM_RATE_LIMITED, etc. - paymentx-llm-service ka
 * LlmErrorCodes dekho) ek generic ke bajaye, taaki frontend "provider
 * ke paas credentials configured hi nahi hain" ko "provider rate-
 * limited hai" se aur "LLM Service unreachable hai" se alag bata sake.
 * componentHealth() ek real /actuator/health ko dono me se kisi ek
 * service par probe karta hai (free, unauthenticated, koi billed
 * provider call nahi - wahi honesty constraint jo LLM Service ka apna
 * GET /api/v1/llm/health khud par apply karta hai) aur UP -> READY /
 * kuch aur -> NOT_READY map karta hai, taaki AiHealthResponse.components
 * finally promptService/llmService ke liye ek hardcoded NOT_IMPLEMENTED
 * se kuch aur report kar sake ab jab dono real services exist karti
 * hain. Ye kyu hai: Phase 3.3 brief ka Step 10. Backend se kaise connect
 * hogi: AiChatService ise call karta hai; sirf kabhi
 * ControlCenterProperties.Ai ke server-configured URLs target karta
 * hai, kabhi ek browser-supplied nahi (wahi SSRF boundary jo
 * ServiceHealthClient ka apna javadoc 9 business services ke liye
 * document karta hai).
 */
@Component
@Slf4j
public class AiPlatformClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPlatformClient(RestTemplate aiServiceRestTemplate) {
        this.restTemplate = aiServiceRestTemplate;
    }

    public record LlmGenerateResult(String content, boolean refused, String stopReason) {
    }

    /**
     * ENGLISH: Phase 3.6 addition - result of a real RAG Service query.
     * `status` is RAG Service's own real value (SUCCESS/
     * INSUFFICIENT_CONTEXT/REFUSED, verbatim - see paymentx-rag-service's
     * RagQueryStatus javadoc), not reinterpreted here; AiChatService maps
     * it into the existing AiChatResponse.status field so the frontend's
     * already-real classifyAiError/rendering logic needs no change.
     *
     * HINGLISH: Phase 3.6 addition - ek real RAG Service query ka
     * result. `status` RAG Service ki apni real value hai (SUCCESS/
     * INSUFFICIENT_CONTEXT/REFUSED, verbatim - paymentx-rag-service ka
     * RagQueryStatus javadoc dekho), yahan reinterpret nahi hoti;
     * AiChatService ise existing AiChatResponse.status field me map
     * karta hai taaki frontend ke already-real classifyAiError/rendering
     * logic ko koi change na chahiye.
     */
    public record RagQueryResult(String answer, String status) {
    }

    /**
     * ENGLISH: Phase 3.8 addition - result of a real Agent Orchestrator
     * execution. `status` is Agent Orchestrator's own real value
     * (SUCCESS/INSUFFICIENT_CONTEXT/REFUSED/DENIED/MAX_ITERATIONS/
     * TIMEOUT/FAILED, verbatim - see paymentx-agent-orchestrator's
     * AgentResponseStatus javadoc), not reinterpreted here - same
     * verbatim-passthrough precedent RagQueryResult already established
     * in Phase 3.6, just with three additional honest outcomes Agent
     * Orchestrator can report that RAG Service alone never could
     * (DENIED, MAX_ITERATIONS, TIMEOUT).
     *
     * HINGLISH: Phase 3.8 addition - ek real Agent Orchestrator
     * execution ka result. `status` Agent Orchestrator ki apni real
     * value hai (SUCCESS/INSUFFICIENT_CONTEXT/REFUSED/DENIED/
     * MAX_ITERATIONS/TIMEOUT/FAILED, verbatim - paymentx-agent-
     * orchestrator ka AgentResponseStatus javadoc dekho), yahan
     * reinterpret nahi hoti - RagQueryResult ne Phase 3.6 me already
     * establish kiya wahi verbatim-passthrough precedent, bas teen
     * additional honest outcomes ke saath jo Agent Orchestrator report
     * kar sakta hai jo RAG Service akele kabhi nahi kar sakti thi
     * (DENIED, MAX_ITERATIONS, TIMEOUT).
     */
    public record AgentExecuteResult(String answer, String status) {
    }

    public LlmGenerateResult generate(String llmServiceUrl, String prompt, String correlationId) {
        String url = llmServiceUrl + "/api/v1/llm/generate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(CorrelationIdFilter.HEADER_NAME, correlationId);
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(Map.of("prompt", prompt), headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode()) {
                throw new AiServiceNotReadyException("AI_SERVICE_NOT_READY", "LLM Service returned an empty response body.");
            }
            return new LlmGenerateResult(
                    data.path("content").asText(""),
                    data.path("refused").asBoolean(false),
                    data.path("stopReason").asText(null));
        } catch (RestClientResponseException httpError) {
            JsonNode errorBody = parseBodySafely(httpError.getResponseBodyAsString());
            String errorCode = errorBody != null ? errorBody.path("error").path("errorCode").asText("AI_SERVICE_NOT_READY") : "AI_SERVICE_NOT_READY";
            String message = errorBody != null && !errorBody.path("error").path("message").isMissingNode()
                    ? errorBody.path("error").path("message").asText()
                    : "LLM Service call failed with HTTP " + httpError.getStatusCode().value();
            log.warn("LLM Service generate call failed httpStatus={} errorCode={}", httpError.getStatusCode().value(), errorCode);
            throw new AiServiceNotReadyException(errorCode, message);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("LLM Service unreachable reason={}", connectionFailure.getMessage());
            throw new AiServiceNotReadyException("AI_SERVICE_NOT_READY",
                    "Could not reach LLM Service: " + connectionFailure.getMostSpecificCause().getMessage());
        }
    }

    /**
     * ENGLISH: Calls RAG Service's real POST /api/v1/rag/query - the
     * exact same JsonNode-parsing, real-errorCode-passthrough pattern
     * generate() above already established for LLM Service. Never
     * treats a real INSUFFICIENT_CONTEXT/REFUSED status as an HTTP
     * error - those are RAG Service's own honest, non-error outcomes
     * (see RagQueryStatus's javadoc), returned here as-is inside a
     * normal RagQueryResult, exactly the way LlmGenerateResult already
     * carries `refused` as data, not an exception.
     *
     * HINGLISH: RAG Service ka real POST /api/v1/rag/query call karta
     * hai - upar generate() ne LLM Service ke liye already establish
     * kiya wahi exact JsonNode-parsing, real-errorCode-passthrough
     * pattern. Ek real INSUFFICIENT_CONTEXT/REFUSED status ko kabhi ek
     * HTTP error treat nahi karta - wo RAG Service ke apne honest, non-
     * error outcomes hain (RagQueryStatus ka javadoc dekho), yahan as-is
     * ek normal RagQueryResult ke andar return hote hain, exactly wahi
     * tarike se jaise LlmGenerateResult already `refused` ko data ke
     * roop me carry karta hai, ek exception nahi.
     */
    public RagQueryResult queryRag(String ragServiceUrl, String query, String correlationId) {
        String url = ragServiceUrl + "/api/v1/rag/query";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(CorrelationIdFilter.HEADER_NAME, correlationId);
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(Map.of("query", query), headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode()) {
                throw new AiServiceNotReadyException("AI_SERVICE_NOT_READY", "RAG Service returned an empty response body.");
            }
            return new RagQueryResult(data.path("answer").asText(""), data.path("status").asText("SUCCESS"));
        } catch (RestClientResponseException httpError) {
            JsonNode errorBody = parseBodySafely(httpError.getResponseBodyAsString());
            String errorCode = errorBody != null ? errorBody.path("error").path("errorCode").asText("AI_SERVICE_NOT_READY") : "AI_SERVICE_NOT_READY";
            String message = errorBody != null && !errorBody.path("error").path("message").isMissingNode()
                    ? errorBody.path("error").path("message").asText()
                    : "RAG Service call failed with HTTP " + httpError.getStatusCode().value();
            log.warn("RAG Service query call failed httpStatus={} errorCode={}", httpError.getStatusCode().value(), errorCode);
            throw new AiServiceNotReadyException(errorCode, message);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("RAG Service unreachable reason={}", connectionFailure.getMessage());
            throw new AiServiceNotReadyException("AI_SERVICE_NOT_READY",
                    "Could not reach RAG Service: " + connectionFailure.getMostSpecificCause().getMessage());
        }
    }

    /**
     * ENGLISH: Calls Agent Orchestrator's real POST /api/v1/agent/execute
     * - the exact same JsonNode-parsing, real-status-passthrough pattern
     * queryRag() above already established for RAG Service. Never
     * treats a real INSUFFICIENT_CONTEXT/REFUSED/DENIED/MAX_ITERATIONS/
     * TIMEOUT status as an HTTP error - those are Agent Orchestrator's
     * own honest, non-error outcomes (see AgentResponseStatus's
     * javadoc), returned here as-is inside a normal AgentExecuteResult.
     *
     * HINGLISH: Agent Orchestrator ka real POST /api/v1/agent/execute
     * call karta hai - upar queryRag() ne RAG Service ke liye already
     * establish kiya wahi exact JsonNode-parsing, real-status-
     * passthrough pattern. Ek real INSUFFICIENT_CONTEXT/REFUSED/DENIED/
     * MAX_ITERATIONS/TIMEOUT status ko kabhi ek HTTP error treat nahi
     * karta - wo Agent Orchestrator ke apne honest, non-error outcomes
     * hain (AgentResponseStatus ka javadoc dekho), yahan as-is ek normal
     * AgentExecuteResult ke andar return hote hain.
     */
    public AgentExecuteResult executeAgent(String agentOrchestratorUrl, String userQuery, String conversationId, String correlationId) {
        String url = agentOrchestratorUrl + "/api/v1/agent/execute";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.add(CorrelationIdFilter.HEADER_NAME, correlationId);
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("conversationId", conversationId);
        body.put("userQuery", userQuery);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
            if (data == null || data.isMissingNode()) {
                throw new AiServiceNotReadyException("AI_SERVICE_NOT_READY", "Agent Orchestrator returned an empty response body.");
            }
            return new AgentExecuteResult(data.path("answer").asText(""), data.path("status").asText("FAILED"));
        } catch (RestClientResponseException httpError) {
            JsonNode errorBody = parseBodySafely(httpError.getResponseBodyAsString());
            String errorCode = errorBody != null ? errorBody.path("error").path("errorCode").asText("AI_SERVICE_NOT_READY") : "AI_SERVICE_NOT_READY";
            String message = errorBody != null && !errorBody.path("error").path("message").isMissingNode()
                    ? errorBody.path("error").path("message").asText()
                    : "Agent Orchestrator call failed with HTTP " + httpError.getStatusCode().value();
            log.warn("Agent Orchestrator execute call failed httpStatus={} errorCode={}", httpError.getStatusCode().value(), errorCode);
            throw new AiServiceNotReadyException(errorCode, message);
        } catch (ResourceAccessException connectionFailure) {
            log.warn("Agent Orchestrator unreachable reason={}", connectionFailure.getMessage());
            throw new AiServiceNotReadyException("AI_SERVICE_NOT_READY",
                    "Could not reach Agent Orchestrator: " + connectionFailure.getMostSpecificCause().getMessage());
        }
    }

    public AiComponentStatus componentHealth(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return AiComponentStatus.NOT_READY;
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/actuator/health", HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
            String status = response.getBody() != null ? response.getBody().path("status").asText(null) : null;
            return "UP".equals(status) ? AiComponentStatus.READY : AiComponentStatus.NOT_READY;
        } catch (Exception probeFailure) {
            log.debug("AI platform component health probe failed baseUrl={} reason={}", baseUrl, probeFailure.getMessage());
            return AiComponentStatus.NOT_READY;
        }
    }

    private JsonNode parseBodySafely(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return null;
        }
    }
}
