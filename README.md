# PaymentX

A real-time payment orchestration platform, built as a production-grade
learning project modeled on how real fintech companies (Stripe, Razorpay,
Nium, Modulr-class platforms) architect payment rails.

## Module 1 Status: Project Setup ✅

- [x] Multi-module Maven structure (parent + 9 services + common)
- [x] Shared contracts library (`paymentx-common`) — event envelope, exception
      hierarchy, correlation-ID propagation filter
- [x] Local infra via Docker Compose — Postgres (per-service DBs), Kafka
      (KRaft mode), Redis, RabbitMQ, Kafka UI
- [x] First ADR establishing the monorepo decision
- [x] Dockerfile pattern (multi-stage, non-root, layer-cache-optimized)

## Prerequisites

- JDK 17
- Maven 3.9+
- Docker + Docker Compose

## Local Development

```bash
# 1. Start infrastructure
cd infra
docker-compose up -d

# 2. Verify everything is healthy
docker-compose ps

# 3. Build the whole platform
cd ..
mvn clean install

# 4. Run a single service (example: auth-service)
cd paymentx-auth-service
mvn spring-boot:run
```

Kafka UI: http://localhost:8090
RabbitMQ Management: http://localhost:15672 (guest/guest)

## Architecture Decision Records

See `docs/adr/` — every non-trivial structural decision is documented there
with context, decision, and consequences. Start with `0001-multi-module-monorepo.md`.

## Module Roadmap

1. **Project Setup** ← we are here
2. Validation Service + Database Design
3. Auth Service — JWT issuance/verification, Spring Security
4. Kafka Event Bus — topics, partitioning strategy, idempotent consumers
5. Payment Service — orchestration core, scheme selection logic
6. Routing Service — InstantPayment/CARD_PAYMENT/REAL_TIME_PAYMENT protocol adapters
7. Audit / Reconciliation / Reporting services
8. Notification Service — RabbitMQ exchange/queue design
9. Observability — OpenTelemetry, Prometheus, Grafana
10. AWS Deployment — ECS/EKS, Terraform
11. AI Layer — Payment Error Analyzer (LLM), RAG-based RCA, MCP integration
