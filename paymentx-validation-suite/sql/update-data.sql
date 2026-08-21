-- ============================================================================
-- PaymentX Validation Suite - update-data.sql
-- ============================================================================
-- Demonstrates real UPDATE operations against validation-suite-owned fixture
-- rows (inserted by insert-data.sql). Deliberately does NOT touch
-- Liquibase-seeded baseline rows (BANK001/BANK002, default routing rules,
-- report_catalog) - those are the platform's source-of-truth fixtures and
-- other tests/services depend on them being exactly what the changelog put
-- there. Safe to re-run.
-- ============================================================================

\c paymentx_validation

-- Re-affirm BANK004 stays SUSPENDED (idempotent no-op if already set) -
-- exercises the same UPDATE path a real participant-suspension admin
-- action would take.
UPDATE participant
SET status = 'SUSPENDED', updated_at = now()
WHERE bank_id = 'BANK004';

-- Bump BANK003's legal name to prove UPDATE is wired end-to-end
-- (idempotent: setting to its own target value).
UPDATE participant
SET legal_name = 'Third Test Bank (Instant-only)', updated_at = now()
WHERE bank_id = 'BANK003';

\c paymentx_routing

-- Raise BANK999's override priority to demonstrate a real routing-rule
-- update (lower number = higher priority; this keeps it ahead of the
-- INSTANT_PAYMENT default at priority 100).
UPDATE routing_rule
SET priority = 15, updated_at = now(), updated_by = 'VALIDATION_SUITE'
WHERE scheme = 'INSTANT_PAYMENT' AND participant_id = 'BANK999';

\c paymentx_reporting

-- Advance the validation-suite's report schedule's next_run_at, exercising
-- the same UPDATE ReportScheduler itself performs after each run.
UPDATE report_schedule
SET next_run_at = now() + interval '1 day', updated_at = now(), updated_by = 'VALIDATION_SUITE'
WHERE report_type = 'PAYMENT_SUMMARY' AND frequency = 'DAILY';
