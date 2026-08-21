-- ============================================================================
-- PaymentX Validation Suite - reset.sql
-- ============================================================================
-- Wipes ALL transactional/generated data across every service database back
-- to a freshly-migrated state - broader than cleanup.sql, which only removes
-- rows this suite itself tagged. Use this when you want a completely clean
-- environment (e.g. before a from-scratch demo), not just a post-test tidy-up.
--
-- Uses TRUNCATE ... CASCADE on transactional tables only. Deliberately
-- PRESERVES:
--   - Liquibase's own bookkeeping (databasechangelog, databasechangeloglock)
--     - never touched, so `mvn`/app startup won't try to re-run migrations.
--   - Reference/config tables that are the platform's source of truth:
--     participant, participant_scheme, business_rule, blacklist, certificate
--     (validation-service); routing_rule (routing-service); report_catalog,
--     report_schedule (reporting-service).
-- Re-run insert-data.sql afterwards to restore the validation-suite's own
-- supplementary fixtures (BANK003/BANK004/BANK999 etc.) if needed.
-- ============================================================================

\c paymentx_payment
TRUNCATE TABLE payment_status_history, payment_audit, payment_retry, payment_settlement, payment_outbox, payment CASCADE;

\c paymentx_validation
TRUNCATE TABLE idempotency_record, validation_log CASCADE;

\c paymentx_audit
TRUNCATE TABLE audit_event CASCADE;

\c paymentx_notification
TRUNCATE TABLE delivery_attempt, notification CASCADE;

\c paymentx_reconciliation
-- reconciliation_record, mismatch_record, reconciliation_summary all cascade
-- from reconciliation_batch; settlement_file is independent (import source
-- records, not derived from a batch).
TRUNCATE TABLE reconciliation_batch, settlement_file CASCADE;

\c paymentx_reporting
-- report_execution (and its cascading children report_result,
-- report_metadata, report_export) cascade from report_request.
TRUNCATE TABLE report_request CASCADE;

\c paymentx_routing
-- No transactional table exists in routing-service (routing_rule is
-- reference/config data, deliberately preserved) - nothing to truncate here.
