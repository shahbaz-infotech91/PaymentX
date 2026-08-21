package com.paymentx.controlcenter.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * ENGLISH: The single typed configuration surface for everything this
 * dashboard talks to. What it does: binds every control-center.* key
 * from application.yml - frontend CORS origins, each of the 9 real
 * PaymentX service base URLs, the 7 real Liquibase-managed Postgres
 * database JDBC URLs, and real connection settings for Kafka/Redis/
 * RabbitMQ/Prometheus/Zipkin - into type-safe Java objects. Why it
 * exists: this is the ONE allowlist every client class (see client/)
 * reads from - no controller or client in this module ever accepts a
 * URL/host/port from a browser request; every outbound connection
 * target is one of these server-configured values. This is the actual
 * SSRF defense the Phase 2 brief requires, not a separate filter
 * layered on top. How it will communicate with the backend: consumed
 * by every client/*Client.java and by PostgresDataSourceConfig to
 * build the 7 read-only DataSource beans.
 *
 * HINGLISH: Is dashboard jis-jis cheez se baat karta hai unke liye ek
 * hi typed configuration surface. Ye kya karti hai: application.yml
 * ke har control-center.* key ko - frontend CORS origins, 9 real
 * PaymentX service base URLs, 7 real Liquibase-managed Postgres
 * database JDBC URLs, aur Kafka/Redis/RabbitMQ/Prometheus/Zipkin ke
 * real connection settings - type-safe Java objects me bind karta
 * hai. Ye dashboard me kyu hai: yehi EK allowlist hai jise har client
 * class (client/ dekho) padhti hai - is module ka koi bhi controller
 * ya client kabhi browser request se URL/host/port accept nahi karta;
 * har outbound connection target in server-configured values me se
 * ek hota hai. Yehi actual SSRF defense hai jo Phase 2 brief maangta
 * hai, koi alag se layered filter nahi. Backend se kaise connect
 * hogi: har client/*Client.java aur PostgresDataSourceConfig (jo 7
 * read-only DataSource beans banata hai) ise consume karte hain.
 */
@ConfigurationProperties(prefix = "control-center")
@Data
public class ControlCenterProperties {

    @NestedConfigurationProperty
    private Cors cors = new Cors();

    @NestedConfigurationProperty
    private Services services = new Services();

    @NestedConfigurationProperty
    private Postgres postgres = new Postgres();

    @NestedConfigurationProperty
    private Kafka kafka = new Kafka();

    @NestedConfigurationProperty
    private Redis redis = new Redis();

    @NestedConfigurationProperty
    private Rabbitmq rabbitmq = new Rabbitmq();

    @NestedConfigurationProperty
    private Prometheus prometheus = new Prometheus();

    @NestedConfigurationProperty
    private Zipkin zipkin = new Zipkin();

    @NestedConfigurationProperty
    private MailHog mailhog = new MailHog();

    @NestedConfigurationProperty
    private Files files = new Files();

    @NestedConfigurationProperty
    private Logs logs = new Logs();

    @NestedConfigurationProperty
    private E2E e2e = new E2E();

    @NestedConfigurationProperty
    private Security security = new Security();

    @NestedConfigurationProperty
    private Ai ai = new Ai();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    /** Real base URLs for every existing PaymentX business service - matches each service's real server.port. */
    @Data
    public static class Services {
        private String apiGatewayUrl = "http://localhost:8080";
        private String authServiceUrl = "http://localhost:8081";
        private String validationServiceUrl = "http://localhost:8082";
        private String paymentServiceUrl = "http://localhost:8083";
        private String routingServiceUrl = "http://localhost:8084";
        private String auditServiceUrl = "http://localhost:8085";
        private String notificationServiceUrl = "http://localhost:8086";
        private String reconciliationServiceUrl = "http://localhost:8087";
        private String reportingServiceUrl = "http://localhost:8088";
        /** HTTP client timeouts applied to every one of the 9 service calls above. */
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    /**
     * The 7 real, Liquibase-managed PaymentX databases (paymentx_auth has
     * no schema - auth-service has zero JPA/Postgres dependency, verified
     * during PaymentX observability work - so it is deliberately absent
     * here). Username/password are shared across all 7 since they're the
     * same local Postgres instance (port 5433, matching
     * infra/docker-compose.yml's real port mapping) - never logged (see
     * @ToString.Exclude on password).
     */
    @Data
    public static class Postgres {
        private String host = "localhost";
        private int port = 5433;
        private String username = "postgres";
        @ToString.Exclude
        private String password = "postgres";
        private String validationDb = "paymentx_validation";
        private String paymentDb = "paymentx_payment";
        private String routingDb = "paymentx_routing";
        private String auditDb = "paymentx_audit";
        private String notificationDb = "paymentx_notification";
        private String reconciliationDb = "paymentx_reconciliation";
        private String reportingDb = "paymentx_reporting";
        /** Small pool - this backend only ever runs read-only monitoring queries, never a business workload. */
        private int maxPoolSize = 3;
    }

    @Data
    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        private int adminClientTimeoutMs = 8000;
    }

    @Data
    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private int timeoutMs = 3000;
    }

    /** RabbitMQ management API - guest/guest matches infra/docker-compose.yml's real, documented local-dev default. */
    @Data
    public static class Rabbitmq {
        private String managementUrl = "http://localhost:15672";
        private String username = "guest";
        @ToString.Exclude
        private String password = "guest";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    @Data
    public static class Prometheus {
        private String baseUrl = "http://localhost:9090";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 8000;
    }

    @Data
    public static class Zipkin {
        private String baseUrl = "http://localhost:9411";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    /** MailHog's real SMTP-capture web UI/API - infra/docker-compose.yml's real local-dev port mapping (8025). Phase 4 Notification page safe preview/link. */
    @Data
    public static class MailHog {
        private String baseUrl = "http://localhost:8025";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    /**
     * The ONE real, explicitly-configured directory this backend is
     * allowed to list/serve files from (Phase 4 Files browser + real
     * report downloads) - the exact same directory reporting-service
     * itself writes real export files to (see paymentx-reporting-
     * service's application.yml report-export-directory, default
     * /tmp/paymentx-reporting/exports - verified live to contain real
     * .csv/.xlsx/.pdf/.json files on this machine). This is the entire
     * SSRF/path-traversal defense for the Files feature: every request
     * is validated against this one real, server-configured root - no
     * endpoint anywhere accepts a directory path from the browser.
     */
    @Data
    public static class Files {
        private String reportsExportDirectory = "/tmp/paymentx-reporting/exports";
    }

    /**
     * ENGLISH: The ONE real, explicitly-configured directory the Phase 4
     * Log Viewer is allowed to read from - the exact same directory
     * paymentx-validation-suite/scripts/start-all.ps1 already redirects
     * every real running service's real stdout/stderr into (svc-
     * &lt;service&gt;.log / svc-&lt;service&gt;.err.log), verified live to
     * contain real Spring Boot log lines on this machine. Default is
     * relative (backend's working directory is paymentx-control-center/
     * backend when run via mvn/the jar, so "../../paymentx-validation-
     * output" resolves to the real repo-root paymentx-validation-output/
     * directory) - overridable via CONTROL_CENTER_LOGS_OUTPUT_DIRECTORY
     * for an absolute path in other environments.
     *
     * HINGLISH: Wahi EK real, explicitly-configured directory jahan se
     * Phase 4 Log Viewer padhne ki ijazat rakhta hai -
     * paymentx-validation-suite/scripts/start-all.ps1 already har real
     * chal rahi service ka real stdout/stderr isi directory me redirect
     * karta hai (svc-&lt;service&gt;.log / svc-&lt;service&gt;.err.log), is
     * machine par live verify kiya gaya ki isme real Spring Boot log
     * lines hain. Default relative hai (backend ki working directory
     * paymentx-control-center/backend hoti hai jab mvn/jar se chalaya
     * jaata hai, isliye "../../paymentx-validation-output" real
     * repo-root paymentx-validation-output/ directory tak resolve hota
     * hai) - CONTROL_CENTER_LOGS_OUTPUT_DIRECTORY se ek absolute path ke
     * liye override ho sakta hai.
     */
    @Data
    public static class Logs {
        private String outputDirectory = "../../paymentx-validation-output";
        /** Hard cap on raw lines read per file per request - the real "do not load unlimited logs" bound. */
        private int maxLinesScannedPerFile = 5000;
        /** Hard cap on how many parsed entries any single request can return, across all services. */
        private int maxEntriesReturned = 1000;
    }

    /**
     * ENGLISH: Phase 5 E2E Payment Flow orchestration settings. What it
     * does: maxDurationSeconds is the one finite ceiling the whole E2E
     * run is bound by (default 60s - 2x the real 30s payment-appearance
     * poll paymentx-validation-suite/scripts/run-e2e.ps1 itself uses,
     * giving headroom for the same real downstream audit/notification
     * propagation that script separately sleeps for, while staying
     * unambiguously finite - this dashboard NEVER polls forever).
     * apiKeyTtlSeconds is deliberately short (5 minutes, not the
     * validation suite's 1 hour) since a dashboard-triggered run
     * completes or times out within maxDurationSeconds - the real,
     * ephemeral test API key this seeds into Redis should not outlive
     * its own run by much. historyLimit bounds the in-memory run
     * history so it can never grow unbounded across a long-running
     * backend process.
     *
     * HINGLISH: Phase 5 E2E Payment Flow orchestration settings. Ye kya
     * karti hai: maxDurationSeconds wo ek finite ceiling hai jispar
     * poora E2E run bound hota hai (default 60s - real 30s
     * payment-appearance poll ka 2x, jise
     * paymentx-validation-suite/scripts/run-e2e.ps1 khud use karta hai,
     * usi real downstream audit/notification propagation ke liye
     * headroom deta hai jiske liye wo script alag se sleep karta hai,
     * fir bhi unambiguously finite rehte hue - ye dashboard KABHI
     * hamesha ke liye poll nahi karta). apiKeyTtlSeconds jaan-boojh kar
     * chhota hai (5 minute, validation suite ke 1 hour ke bajaye)
     * kyunki ek dashboard-triggered run maxDurationSeconds ke andar hi
     * complete ya timeout ho jaata hai - wo real, ephemeral test API
     * key jo ye Redis me seed karta hai apne run se zyada der tak nahi
     * bachni chahiye. historyLimit in-memory run history ko bound karta
     * hai taaki ek lambe chalne wale backend process ke across ye kabhi
     * unbounded na badhe.
     */
    @Data
    public static class E2E {
        private int maxDurationSeconds = 60;
        private int pollIntervalMillis = 1500;
        private int apiKeyTtlSeconds = 300;
        private int historyLimit = 20;
    }

    /**
     * ENGLISH: Phase 6 production-hardening authentication gate -
     * deliberately NOT spring-boot-starter-security (see security/
     * SecurityConfig.java's own javadoc for why a full security starter
     * with no real IdP behind it is dishonest scaffolding). What it
     * does: enabled defaults to false so this module's local-dev
     * behavior - verified across every prior phase - never changes
     * without an operator opting in; when true, DashboardAuthFilter
     * requires a real "Authorization: Bearer &lt;dashboardToken&gt;"
     * header on every /api/** call except /api/v1/health. dashboardToken
     * has NO default value on purpose - DashboardAuthFilter refuses to
     * start the application if enabled=true with a blank token, a real
     * fail-closed check rather than silently running unauthenticated in
     * an environment an operator believed was protected. This is
     * single-shared-token gating (protects the dashboard's own API from
     * unauthenticated network access), not multi-user
     * authentication/authorization - there is no real per-user IdP
     * behind PaymentX yet (auth-service has zero endpoints, confirmed
     * live) for this module to integrate with.
     *
     * HINGLISH: Phase 6 production-hardening authentication gate -
     * jaan-boojh kar spring-boot-starter-security NAHI hai (security/
     * SecurityConfig.java ka apna javadoc dekho ki bina kisi real IdP ke
     * ek pura security starter kyun dishonest scaffolding hai). Ye kya
     * karti hai: enabled default false hai taaki is module ka local-dev
     * behavior - har pichle phase me verify kiya gaya - kabhi bina ek
     * operator ke opt-in kiye badle nahi; jab true ho, DashboardAuthFilter
     * har /api/** call par ek real "Authorization: Bearer
     * &lt;dashboardToken&gt;" header maangta hai, sivaay /api/v1/health
     * ke. dashboardToken ka jaan-boojh kar koi default value nahi hai -
     * DashboardAuthFilter application start hi nahi hone deta agar
     * enabled=true ho aur token blank ho, ek real fail-closed check,
     * silently unauthenticated chalne ke bajaye ek aise environment me
     * jise operator protected samajhta tha. Ye single-shared-token
     * gating hai (dashboard ke apne API ko unauthenticated network
     * access se protect karta hai), multi-user authentication/
     * authorization nahi - PaymentX ke peeche abhi koi real per-user IdP
     * nahi hai (auth-service ke zero endpoints hain, live confirm kiya
     * gaya) jise ye module integrate kar sake.
     */
    @Data
    public static class Security {
        private boolean enabled = false;
        @ToString.Exclude
        private String dashboardToken;
    }

    /**
     * ENGLISH: Phase 3.1 AI Assistant feature toggle - see
     * PAYMENTX_PHASE_3_1_AI_CHAT_INTERFACE.md. What it does: enabled
     * defaults to false (this repository's standard off-by-default
     * posture for anything not yet real - see Security above for the
     * identical pattern), so AiController honestly reports
     * "AI_NOT_CONFIGURED" instead of "AI_SERVICE_NOT_READY" until an
     * operator deliberately turns this on. Turning it on does NOT make
     * AI chat functional - no LLM/Prompt/RAG/MCP/Agent Orchestrator
     * service exists yet (Phase 3.2-3.8) - it only changes which honest
     * "not available" reason the frontend is told. Why it exists: gives
     * operators a real, meaningful lever instead of a permanently-true
     * placeholder, and matches the `ai.enabled` config key already
     * planned in the Phase 3.0 architecture audit. How it will
     * communicate with the backend: read by AiChatService for both
     * POST /api/v1/ai/chat and GET /api/v1/ai/health.
     *
     * HINGLISH: Phase 3.1 AI Assistant feature toggle -
     * PAYMENTX_PHASE_3_1_AI_CHAT_INTERFACE.md dekho. Ye kya karti hai:
     * enabled default false hai (is repo ka standard off-by-default
     * rawaiya kisi bhi cheez ke liye jo abhi real nahi hai - upar
     * Security dekho identical pattern ke liye), taaki AiController
     * honestly "AI_NOT_CONFIGURED" report kare "AI_SERVICE_NOT_READY"
     * ke bajaye, jab tak ek operator jaan-boojh kar ise on na kare. Ise
     * on karne se AI chat functional NAHI ho jaata - abhi koi LLM/
     * Prompt/RAG/MCP/Agent Orchestrator service exist hi nahi karti
     * (Phase 3.2-3.8) - ye sirf ye badalta hai ki frontend ko konsi
     * honest "not available" reason batayi jaaye. Ye dashboard me kyu
     * hai: operators ko ek real, meaningful lever deta hai, ek
     * permanently-true placeholder ke bajaye, aur Phase 3.0 architecture
     * audit me already planned `ai.enabled` config key se match karta
     * hai. Backend se kaise connect hogi: AiChatService dono POST
     * /api/v1/ai/chat aur GET /api/v1/ai/health ke liye ise padhta hai.
     */
    /**
     * ENGLISH: Phase 3.3 addition to the Phase 3.1 Ai settings above -
     * real base URLs for the two real AI Platform backend services that
     * now exist (Prompt Service, Phase 3.2; LLM Service, Phase 3.3).
     * What it does: gives AiChatService/AiPlatformClient the same kind
     * of server-configured, browser-never-supplies-a-URL allowlist
     * Services already provides for the 9 real business services (see
     * that class's own SSRF-defense javadoc) - AiPlatformClient never
     * accepts a URL from anywhere except this class. readTimeoutMs
     * defaults far higher than Services.readTimeoutMs (5000ms) because
     * a real LLM generation call - unlike a simple Actuator health
     * check - can legitimately take many seconds. Why it exists: Step
     * 10 of the Phase 3.3 brief - "update AiChatService to actually
     * call Prompt Service then LLM Service... where configured."
     * How it will communicate with the backend: read by
     * HttpClientConfig.aiServiceRestTemplate and by AiPlatformClient/
     * AiChatService.
     *
     * HINGLISH: Upar wale Phase 3.1 Ai settings me Phase 3.3 ka addition
     * - un do real AI Platform backend services ke real base URLs jo ab
     * exist karte hain (Prompt Service, Phase 3.2; LLM Service, Phase
     * 3.3). Ye kya karti hai: AiChatService/AiPlatformClient ko wahi
     * kism ka server-configured, browser-kabhi-URL-nahi-deta allowlist
     * deta hai jo Services already 9 real business services ke liye
     * deti hai (us class ka apna SSRF-defense javadoc dekho) -
     * AiPlatformClient kabhi kahin aur se URL accept nahi karta, sirf is
     * class se. readTimeoutMs Services.readTimeoutMs (5000ms) se kaafi
     * zyada default hai kyunki ek real LLM generation call - ek simple
     * Actuator health check ke ulat - genuinely kai seconds le sakti
     * hai. Ye kyu hai: Phase 3.3 brief ka Step 10 - "AiChatService ko
     * update karo taaki wo actually Prompt Service phir LLM Service ko
     * call kare... jahan configured ho."
     * Backend se kaise connect hogi: HttpClientConfig.aiServiceRestTemplate
     * aur AiPlatformClient/AiChatService ise padhte hain.
     */
    @Data
    public static class Ai {
        private boolean enabled = false;
        private String promptServiceUrl = "http://localhost:8092";
        private String llmServiceUrl = "http://localhost:8093";
        /** Phase 3.9 addition - Embedding Service, probed for health only (RAG Service is what actually calls it during retrieval); closes the gap where this component was hardcoded NOT_IMPLEMENTED despite the real service existing since Phase 3.4. */
        private String embeddingServiceUrl = "http://localhost:8094";
        /** Phase 3.6 addition - RAG Service, still probed for health but no longer called directly by sendMessage (Phase 3.8 rewired it to Agent Orchestrator). */
        private String ragServiceUrl = "http://localhost:8096";
        /** Phase 3.7 addition - MCP Gateway, probed for health only (its real MCP protocol is never called by Control Center directly). */
        private String mcpGatewayUrl = "http://localhost:8097";
        /** Phase 3.8 addition - Agent Orchestrator is now what sendMessage actually calls; see AiChatService's javadoc. */
        private String agentOrchestratorUrl = "http://localhost:8098";
        private int connectTimeoutMs = 3000;
        /** Phase 3.9 fix - was 30000ms, which is LOWER than Agent Orchestrator's own `agent.overall-timeout-ms`
         * (45000ms at the time, see paymentx-agent-orchestrator/application.yml). A real end-to-end agent
         * execution (LLM planning call, MCP tool call, RAG retrieval which itself calls Embedding/Vector/
         * Prompt/LLM Service) could legitimately take up to Agent Orchestrator's own budget, so a shorter
         * client-side timeout here meant Control Center gave up on healthy in-flight requests before Agent
         * Orchestrator's own timeout would ever fire - discovered via a real live E2E run in this phase where
         * a genuine (non-erroring) request was cut off with "Request cancelled" at exactly 30s. 60000ms gave
         * real headroom above Agent Orchestrator's 45000ms budget at the time.
         * Phase 3.9 INCREMENTAL re-validation fix - raised again, from 60000ms to 90000ms. Agent
         * Orchestrator's own `overall-timeout-ms` was itself raised to 75000ms in this same incremental
         * phase (see that config's own real-Anthropic-529-overload evidence) - unchanged, this 60000ms
         * value would have reproduced the EXACT SAME "caller times out before a healthy callee" bug this
         * field was already fixed once for, just one layer further out. 90000ms keeps the same ~15000ms
         * headroom-above-the-callee's-own-budget pattern this field's original fix established. */
        private int readTimeoutMs = 90000;
    }
}
