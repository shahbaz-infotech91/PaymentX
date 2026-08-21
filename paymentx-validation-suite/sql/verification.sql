-- ============================================================================
-- PaymentX Validation Suite - verification.sql
-- ============================================================================
-- Read-only. Verifies, for every service database that owns a schema:
--   1. Liquibase bookkeeping (databasechangelog / databasechangeloglock) -
--      confirms every changeset EXECUTED cleanly and the lock is released.
--   2. Schema shape - table count, primary keys, foreign keys, unique/check
--      constraints, indexes (via pg_constraint/pg_indexes, not a hand-kept
--      list, so this can't drift out of sync with the actual schema).
--   3. Baseline + validation-suite business data row counts (participants,
--      participant_scheme, business_rule, routing_rule, report_schedule).
--
-- auth-service is deliberately NOT covered here: it has zero JPA/Postgres
-- dependency (see insert-data.sql's header note) - there is no
-- paymentx_auth schema to verify beyond the empty database itself.
--
-- Safe to run at any time against any environment - never writes, never
-- locks anything beyond the read committed default a SELECT takes.
-- Run with: psql -U postgres -f verification.sql   (or docker exec ... -f)
-- ============================================================================

\pset border 2
\timing off

-- ----------------------------------------------------------------------------
-- Reusable checks, run once per service database via \c.
-- Each block is self-contained and copy-identical across databases so the
-- output is directly comparable service-to-service.
-- ----------------------------------------------------------------------------

\c paymentx_validation
\echo '=== paymentx_validation ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'participant' AS business_data, count(*) AS n FROM participant;
SELECT 'participant_scheme' AS business_data, count(*) AS n FROM participant_scheme;
SELECT 'business_rule' AS business_data, count(*) AS n FROM business_rule;
SELECT 'blacklist' AS business_data, count(*) AS n FROM blacklist;
SELECT 'certificate' AS business_data, count(*) AS n FROM certificate;
-- Baseline participants that every other service's fixtures assume exist.
SELECT 'baseline_participants_present' AS check,
       bool_and(present) AS all_present
  FROM (SELECT bank_id, (bank_id IN (SELECT bank_id FROM participant)) AS present
        FROM (VALUES ('BANK001'), ('BANK002')) AS req(bank_id)) x;

\c paymentx_payment
\echo '=== paymentx_payment ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'payment' AS business_data, count(*) AS n FROM payment;

\c paymentx_routing
\echo '=== paymentx_routing ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'routing_rule' AS business_data, count(*) AS n FROM routing_rule;
SELECT 'routing_rule_default_per_scheme' AS check, scheme, count(*) AS n
  FROM routing_rule WHERE is_default = true GROUP BY scheme ORDER BY scheme;

\c paymentx_audit
\echo '=== paymentx_audit ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'audit_event' AS business_data, count(*) AS n FROM audit_event;

\c paymentx_notification
\echo '=== paymentx_notification ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'notification' AS business_data, count(*) AS n FROM notification;
SELECT 'delivery_attempt' AS business_data, count(*) AS n FROM delivery_attempt;

\c paymentx_reconciliation
\echo '=== paymentx_reconciliation ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'reconciliation_batch' AS business_data, count(*) AS n FROM reconciliation_batch;
SELECT 'settlement_file' AS business_data, count(*) AS n FROM settlement_file;
SELECT 'mismatch_record' AS business_data, count(*) AS n FROM mismatch_record;

\c paymentx_reporting
\echo '=== paymentx_reporting ==='
SELECT 'liquibase_lock' AS check, id, locked, lockgranted FROM databasechangeloglock;
SELECT 'liquibase_changesets' AS check, exectype, count(*) FROM databasechangelog GROUP BY exectype ORDER BY exectype;
SELECT 'tables' AS check, count(*) AS n FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'constraints' AS check, contype, count(*) AS n
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname='public'
  GROUP BY contype ORDER BY contype;
SELECT 'indexes' AS check, count(*) AS n FROM pg_indexes WHERE schemaname='public';
SELECT 'report' AS business_data, count(*) AS n FROM report;
SELECT 'report_schedule' AS business_data, count(*) AS n FROM report_schedule;
SELECT 'report_execution' AS business_data, count(*) AS n FROM report_execution;

-- ============================================================================
-- NOT covered by this script (documented so absence isn't mistaken for an
-- oversight - see insert-data.sql's header for the full rationale):
--   - paymentx_auth: no schema exists (service has no DB dependency).
--   - API Keys: live in Redis (key "gateway:apikey:{key}"), not SQL -
--     verify with `redis-cli --scan --pattern "gateway:apikey:*"`.
--   - Currencies: no reference table; currency is a validated free-form
--     string field on the payment request DTO.
--   - Notification Configuration / Payment Configuration / Reconciliation
--     Configuration / Reporting Configuration: application.yml properties,
--     not database rows - verify by reading each service's
--     src/main/resources/application*.yml.
-- ============================================================================
