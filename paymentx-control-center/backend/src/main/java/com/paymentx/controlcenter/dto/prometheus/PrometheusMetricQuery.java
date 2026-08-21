package com.paymentx.controlcenter.dto.prometheus;

/**
 * ENGLISH: The fixed, server-defined set of PromQL queries this
 * backend is allowed to run against Prometheus - covering request
 * rate, latency, error rate, CPU, memory, JVM, GC, threads, Kafka
 * consumer lag, Redis (Lettuce) client latency, and real PaymentX
 * business metrics. What it does: pairs a stable slug with the exact
 * PromQL text, both built from metric names confirmed live against
 * this environment's real Prometheus (GET /api/v1/label/__name__/
 * values), not guessed from Micrometer documentation. Why it exists:
 * this enum IS the "no raw PromQL from the browser" boundary - every
 * controller endpoint takes a slug (an enum value bound from a path
 * segment), never a query string, so a caller can never inject
 * arbitrary PromQL (which could otherwise be used for denial-of-
 * service via expensive queries, or to probe unrelated metrics). How
 * it will communicate with the backend: resolved by
 * PrometheusMetricsService, executed by PrometheusClient.
 *
 * HINGLISH: Fixed, server-defined PromQL queries ka set jinhe ye
 * backend Prometheus ke against chalane ki ijazat rakhta hai - request
 * rate, latency, error rate, CPU, memory, JVM, GC, threads, Kafka
 * consumer lag, Redis (Lettuce) client latency, aur real PaymentX
 * business metrics cover karte hue. Ye kya karti hai: ek stable slug
 * ko exact PromQL text ke saath pair karta hai, dono is environment ke
 * real Prometheus ke against live confirm kiye gaye metric names se
 * bane hain (GET /api/v1/label/__name__/values), Micrometer
 * documentation se guess nahi kiye gaye. Ye dashboard me kyu hai: ye
 * enum "browser se raw PromQL nahi" boundary HAI - har controller
 * endpoint ek slug leta hai (ek enum value jo path segment se bind
 * hoti hai), kabhi query string nahi, isliye ek caller kabhi arbitrary
 * PromQL inject nahi kar sakta (jo warna expensive queries ke through
 * denial-of-service ke liye, ya unrelated metrics probe karne ke liye
 * use ho sakta tha). Backend se kaise connect hogi:
 * PrometheusMetricsService ise resolve karti hai, PrometheusClient ise
 * execute karta hai.
 */
public enum PrometheusMetricQuery {
    REQUEST_RATE("request-rate",
            "Requests per second per service (5m rate)",
            "sum(rate(http_server_requests_seconds_count[5m])) by (job)"),
    REQUEST_LATENCY_P99("request-latency-p99",
            "99th percentile HTTP request latency per service",
            "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (job, le))"),
    ERROR_RATE("error-rate",
            "Fraction of HTTP requests resulting in a 5xx outcome per service (5m)",
            "sum(rate(http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"}[5m])) by (job) / sum(rate(http_server_requests_seconds_count[5m])) by (job)"),
    CPU_USAGE("cpu-usage",
            "Process CPU usage ratio (0-1) per service",
            "process_cpu_usage"),
    SYSTEM_CPU_USAGE("system-cpu-usage",
            "Host system CPU usage ratio (0-1) per service",
            "system_cpu_usage"),
    MEMORY_USED("memory-used",
            "JVM memory used in bytes per service and memory area",
            "jvm_memory_used_bytes"),
    MEMORY_MAX("memory-max",
            "JVM max memory in bytes per service and memory area",
            "jvm_memory_max_bytes"),
    GC_PAUSE_RATE("gc-pause-rate",
            "GC pause time accumulated per second (5m rate) per service",
            "rate(jvm_gc_pause_seconds_sum[5m])"),
    THREADS_LIVE("threads-live",
            "Live JVM thread count per service",
            "jvm_threads_live_threads"),
    KAFKA_CONSUMER_LAG("kafka-consumer-lag",
            "Max observed Kafka consumer lag in seconds, where instrumented (audit/notification consumers)",
            "max({__name__=~\".*_kafka_consumer_lag_seconds_max\"}) by (__name__, job)"),
    REDIS_COMMAND_LATENCY("redis-command-latency",
            "Max Lettuce (Redis client) command completion latency in seconds per service",
            "lettuce_command_completion_seconds_max"),
    BUSINESS_ROUTING_CACHE_MISS_RATE("business-routing-cache-miss-rate",
            "Routing rule cache miss rate per second (5m)",
            "rate(routing_cache_miss_total[5m])"),
    BUSINESS_ROUTING_LOOKUP_LATENCY_P95("business-routing-lookup-latency-p95",
            "95th percentile routing rule lookup latency",
            "histogram_quantile(0.95, sum(rate(routing_route_lookup_seconds_bucket[5m])) by (le))"),
    BUSINESS_ROUTING_RULES_ACTIVE("business-routing-rules-active",
            "Currently active routing rules count, as tracked by routing-service",
            "routing_rules_active_count"),
    BUSINESS_RECONCILIATION_BATCH_DURATION_MAX("business-reconciliation-batch-duration-max",
            "Max observed reconciliation batch duration in seconds",
            "reconciliation_batch_duration_seconds_max"),

    // ================================================================
    // Phase 3.10.3 - AI Platform metrics. Every metric name below is a real, already-existing
    // Micrometer meter registered by hand in the AI Platform service that owns it (LlmMetrics/
    // EmbeddingMetrics/VectorMetrics/RagMetrics/McpMetrics/AgentMetrics/PromptMetrics - see each
    // class's own javadoc) and already scraped by Prometheus (infra/prometheus.yml) - no new
    // instrumentation, no duplicate metric, was added anywhere for this phase. Slugs share the "ai-"
    // prefix (mirroring "business-" above) so PrometheusMetricsService.runAi() can select exactly this
    // group without a second enum/field. Only the operational subset Phase 3.10.3's design doc
    // prioritizes is covered here (requests/success/failure/latency per service) - not all ~60 AI
    // meters that exist; see PAYMENTX_PHASE_3_10_3_AI_METRICS_UI_DESIGN.md Step 2's explicit "do not
    // add every metric blindly" guidance. `_seconds_bucket` is only used for the one Timer per service
    // that actually calls .publishPercentileHistogram() (confirmed by reading each *Metrics.java file
    // directly, not assumed uniform) - RAG's per-step timers (embedding/vector-search/prompt-render/
    // llm) and Agent's per-tool timer do NOT publish a histogram, so this catalog uses each service's
    // one histogram-backed "headline" latency instead (rag_total_latency, agent_execution_latency).
    AI_LLM_REQUEST_RATE("ai-llm-request-rate",
            "LLM Service generate requests per second, by provider (5m rate)",
            "sum(rate(llm_requests_total[5m])) by (provider)"),
    AI_LLM_SUCCESS_RATE("ai-llm-success-rate",
            "LLM Service successful generations per second, by provider (5m rate)",
            "sum(rate(llm_generate_success_total[5m])) by (provider)"),
    AI_LLM_FAILURE_RATE("ai-llm-failure-rate",
            "LLM Service failed generations per second, by provider (5m rate)",
            "sum(rate(llm_generate_failure_total[5m])) by (provider)"),
    AI_LLM_LATENCY_P95("ai-llm-latency-p95",
            "95th percentile LLM Service generate latency in seconds",
            "histogram_quantile(0.95, sum(rate(llm_generate_latency_seconds_bucket[5m])) by (le))"),

    AI_RAG_QUERY_RATE("ai-rag-query-rate",
            "RAG Service queries received per second (5m rate)",
            "sum(rate(rag_requests_total[5m]))"),
    AI_RAG_SUCCESS_RATE("ai-rag-success-rate",
            "RAG Service grounded answers produced per second (5m rate)",
            "sum(rate(rag_success_total[5m]))"),
    AI_RAG_INSUFFICIENT_CONTEXT_RATE("ai-rag-insufficient-context-rate",
            "RAG Service queries with no relevant retrieved chunks per second (5m rate)",
            "sum(rate(rag_insufficient_context_total[5m]))"),
    AI_RAG_FAILURE_RATE("ai-rag-failure-rate",
            "RAG Service failed queries per second (5m rate)",
            "sum(rate(rag_failure_total[5m]))"),
    AI_RAG_LATENCY_P95("ai-rag-latency-p95",
            "95th percentile RAG Service total query latency in seconds",
            "histogram_quantile(0.95, sum(rate(rag_total_latency_seconds_bucket[5m])) by (le))"),

    AI_MCP_TOOL_CALL_RATE("ai-mcp-tool-call-rate",
            "MCP Gateway tool invocations per second, by tool (5m rate)",
            "sum(rate(mcp_tool_calls_total[5m])) by (tool)"),
    AI_MCP_TOOL_SUCCESS_RATE("ai-mcp-tool-success-rate",
            "MCP Gateway successful tool invocations per second, by tool (5m rate)",
            "sum(rate(mcp_tool_success_total[5m])) by (tool)"),
    AI_MCP_TOOL_FAILURE_RATE("ai-mcp-tool-failure-rate",
            "MCP Gateway failed tool invocations per second, by tool (5m rate)",
            "sum(rate(mcp_tool_failure_total[5m])) by (tool)"),
    AI_MCP_TOOL_DENIED_RATE("ai-mcp-tool-denied-rate",
            "MCP Gateway authorization-denied tool invocations per second, by tool (5m rate)",
            "sum(rate(mcp_tool_denied_total[5m])) by (tool)"),
    AI_MCP_TOOL_LATENCY_P95("ai-mcp-tool-latency-p95",
            "95th percentile MCP Gateway tool call latency in seconds",
            "histogram_quantile(0.95, sum(rate(mcp_tool_latency_seconds_bucket[5m])) by (le))"),

    AI_AGENT_EXECUTION_RATE("ai-agent-execution-rate",
            "Agent Orchestrator runs received per second (5m rate)",
            "sum(rate(agent_requests_total[5m]))"),
    AI_AGENT_SUCCESS_RATE("ai-agent-success-rate",
            "Agent Orchestrator successful runs per second (5m rate)",
            "sum(rate(agent_success_total[5m]))"),
    AI_AGENT_FAILURE_RATE("ai-agent-failure-rate",
            "Agent Orchestrator failed/denied/timed-out runs per second, by status (5m rate)",
            "sum(rate(agent_failure_total[5m])) by (status)"),
    AI_AGENT_LATENCY_P95("ai-agent-latency-p95",
            "95th percentile Agent Orchestrator run execution latency in seconds",
            "histogram_quantile(0.95, sum(rate(agent_execution_latency_seconds_bucket[5m])) by (le))"),

    AI_EMBEDDING_REQUEST_RATE("ai-embedding-request-rate",
            "Embedding Service requests per second, by provider (5m rate)",
            "sum(rate(embedding_requests_total[5m])) by (provider)"),
    AI_EMBEDDING_LATENCY_P95("ai-embedding-latency-p95",
            "95th percentile Embedding Service latency in seconds",
            "histogram_quantile(0.95, sum(rate(embedding_latency_seconds_bucket[5m])) by (le))"),

    AI_VECTOR_SEARCH_RATE("ai-vector-search-rate",
            "Vector Service searches per second (5m rate)",
            "sum(rate(vector_search_total[5m]))"),
    AI_VECTOR_LATENCY_P95("ai-vector-latency-p95",
            "95th percentile Vector Service search latency in seconds",
            "histogram_quantile(0.95, sum(rate(vector_search_latency_seconds_bucket[5m])) by (le))"),

    AI_PROMPT_REQUEST_RATE("ai-prompt-request-rate",
            "Prompt Service requests per second, by operation (5m rate)",
            "sum(rate(prompt_requests_total[5m])) by (operation)"),
    AI_PROMPT_FAILURE_RATE("ai-prompt-failure-rate",
            "Prompt Service render failures per second (5m rate)",
            "sum(rate(prompt_render_failure_total[5m]))");

    private final String slug;
    private final String description;
    private final String promQl;

    PrometheusMetricQuery(String slug, String description, String promQl) {
        this.slug = slug;
        this.description = description;
        this.promQl = promQl;
    }

    public String slug() {
        return slug;
    }

    public String description() {
        return description;
    }

    public String promQl() {
        return promQl;
    }

    public static PrometheusMetricQuery fromSlug(String slug) {
        for (PrometheusMetricQuery value : values()) {
            if (value.slug.equals(slug)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Prometheus metric query: " + slug);
    }
}
