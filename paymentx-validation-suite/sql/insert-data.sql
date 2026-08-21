-- ============================================================================
-- PaymentX Validation Suite - insert-data.sql
-- ============================================================================
-- WHY this file is SUPPLEMENTARY, not the platform's base seed data:
-- validation-service (V1_0_7__seed_default_data.yaml) and routing-service
-- (V1_0_1__seed_default_routes.yaml) already Liquibase-seed BANK001/BANK002
-- (fully certified for all 3 schemes), amount-limit business rules, a
-- blacklisted account, and default+BANK001-override routing rules on every
-- fresh environment. Re-inserting that here would either violate unique
-- constraints or silently diverge from the source of truth (the changelog).
--
-- This script adds ONLY the extra fixtures the validation suite's negative
-- tests need that the baseline migrations don't provide:
--   - BANK003: certified for INSTANT_PAYMENT only (routing-failure negative test
--     for CARD_PAYMENT/REAL_TIME_PAYMENT - no route can resolve for it)
--   - BANK004: status SUSPENDED (inactive-participant negative test)
--   - BANK999: used by existing RoutingControllerIntegrationTest fixtures -
--     added here too so manual/API-driven runs of the suite have it without
--     depending on test-classpath state
--   - A daily settlement report_schedule row (reporting-service) - proves
--     the scheduler table used by the suite's Step 17 (report generation)
--     isn't empty on a fresh environment
--
-- Idempotent: every INSERT is guarded so this script can be re-run safely
-- against an environment that already has these rows.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- paymentx_validation: supplementary participants + certifications
-- ----------------------------------------------------------------------------
\c paymentx_validation

INSERT INTO participant (bank_id, legal_name, status)
SELECT 'BANK003', 'Third Test Bank (Instant-only)', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM participant WHERE bank_id = 'BANK003');

INSERT INTO participant (bank_id, legal_name, status)
SELECT 'BANK004', 'Fourth Test Bank (Suspended)', 'SUSPENDED'
WHERE NOT EXISTS (SELECT 1 FROM participant WHERE bank_id = 'BANK004');

INSERT INTO participant (bank_id, legal_name, status)
SELECT 'BANK999', 'IT Test Fixture Bank', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM participant WHERE bank_id = 'BANK999');

-- BANK003 is certified for INSTANT_PAYMENT ONLY - a CARD_PAYMENT or
-- REAL_TIME_PAYMENT payment naming BANK003 as debtor/creditor must be
-- REJECTED by validation-service (see V1_0_1 participant_scheme table).
INSERT INTO participant_scheme (participant_id, scheme)
SELECT p.id, 'INSTANT_PAYMENT' FROM participant p WHERE p.bank_id = 'BANK003'
  AND NOT EXISTS (
    SELECT 1 FROM participant_scheme ps WHERE ps.participant_id = p.id AND ps.scheme = 'INSTANT_PAYMENT'
  );

-- BANK999 certified for all three schemes (matches BANK001/BANK002 pattern).
INSERT INTO participant_scheme (participant_id, scheme)
SELECT p.id, s.scheme
FROM participant p, (VALUES ('INSTANT_PAYMENT'), ('CARD_PAYMENT'), ('REAL_TIME_PAYMENT')) AS s(scheme)
WHERE p.bank_id = 'BANK999'
  AND NOT EXISTS (
    SELECT 1 FROM participant_scheme ps WHERE ps.participant_id = p.id AND ps.scheme = s.scheme
  );

-- ----------------------------------------------------------------------------
-- paymentx_routing: participant-specific override for BANK999
-- ----------------------------------------------------------------------------
\c paymentx_routing

INSERT INTO routing_rule (scheme, participant_id, target_route, priority, active, is_default, description, created_by, updated_by)
SELECT 'INSTANT_PAYMENT', 'BANK999', 'instant-payment-processor-bank999', 20, true, false,
       'Validation-suite fixture route for BANK999', 'VALIDATION_SUITE', 'VALIDATION_SUITE'
WHERE NOT EXISTS (
  SELECT 1 FROM routing_rule WHERE scheme = 'INSTANT_PAYMENT' AND participant_id = 'BANK999'
);

-- ----------------------------------------------------------------------------
-- paymentx_reporting: a recurring settlement report schedule
-- ----------------------------------------------------------------------------
\c paymentx_reporting

INSERT INTO report_schedule (report_type, report_format, frequency, cron_expression, active, created_by_user, created_by, updated_by)
SELECT 'PAYMENT_SUMMARY', 'PDF', 'DAILY', '0 0 6 * * *', true, 'VALIDATION_SUITE', 'VALIDATION_SUITE', 'VALIDATION_SUITE'
WHERE NOT EXISTS (
  SELECT 1 FROM report_schedule WHERE report_type = 'PAYMENT_SUMMARY' AND frequency = 'DAILY'
);

-- ============================================================================
-- NOTE ON CATEGORIES THIS FILE DELIBERATELY DOES NOT SEED
-- ============================================================================
-- Users / API Keys: auth-service has NO database (verified: zero JPA/
--   Postgres dependency, only a bare @SpringBootApplication class exists).
--   API keys are validated by api-gateway against REDIS
--   (key "gateway:apikey:{key}" -> participant ID), not a SQL table - see
--   05-init-redis.ps1 for the real seeding mechanism.
-- Currencies: no currency lookup table exists anywhere in the platform;
--   currency is a free-form ISO-4217-pattern-validated string field
--   (@Pattern ^[A-Z]{3}$ on PaymentValidationRequest.currency), not a
--   reference table.
-- Notification Templates: template_name on the notification table is a
--   plain string column, not a foreign key - templates are Java-side
--   constants in EmailTemplateService, not database rows.
-- Settlement Configurations / Reconciliation Configurations: these are
--   application.yml properties (ReconciliationProperties: match tolerance,
--   batch window, etc.), not database tables.
