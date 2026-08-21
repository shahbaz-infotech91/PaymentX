-- ============================================================================
-- PaymentX Validation Suite - cleanup.sql
-- ============================================================================
-- Deletes ONLY the transactional rows a validation-suite E2E run itself
-- creates (payments, audit events, notifications, reconciliation batches,
-- report executions - all tagged with the 'VSUITE-' payment-reference /
-- correlation-id prefix the suite's REST calls use). Safe to run after every
-- E2E execution to keep re-runs clean.
--
-- Deliberately does NOT touch:
--   - Liquibase's own bookkeeping (databasechangelog, databasechangeloglock)
--   - The platform's baseline seed data (BANK001/BANK002, business_rule,
--     default routing_rule rows, report_catalog)
--   - The validation-suite's own supplementary fixtures from insert-data.sql
--     (BANK003/BANK004/BANK999, their routing override, the report_schedule
--     row) - re-run reset.sql if you also want those gone.
-- ============================================================================

\c paymentx_payment

DELETE FROM payment_status_history WHERE payment_id IN (SELECT id FROM payment WHERE payment_reference LIKE 'VSUITE-%');
DELETE FROM payment_audit          WHERE payment_id IN (SELECT id FROM payment WHERE payment_reference LIKE 'VSUITE-%');
DELETE FROM payment_retry          WHERE payment_id IN (SELECT id FROM payment WHERE payment_reference LIKE 'VSUITE-%');
DELETE FROM payment_settlement     WHERE payment_id IN (SELECT id FROM payment WHERE payment_reference LIKE 'VSUITE-%');
DELETE FROM payment_outbox         WHERE payment_id IN (SELECT id FROM payment WHERE payment_reference LIKE 'VSUITE-%');
DELETE FROM payment                WHERE payment_reference LIKE 'VSUITE-%';

\c paymentx_validation

DELETE FROM idempotency_record WHERE payment_reference LIKE 'VSUITE-%';
DELETE FROM validation_log     WHERE payment_reference LIKE 'VSUITE-%';

\c paymentx_audit

DELETE FROM audit_event WHERE reference LIKE 'VSUITE-%' OR correlation_id LIKE 'VSUITE-%';

\c paymentx_notification

DELETE FROM delivery_attempt WHERE notification_id IN (SELECT id FROM notification WHERE correlation_id LIKE 'VSUITE-%' OR payment_id LIKE 'VSUITE-%');
DELETE FROM notification     WHERE correlation_id LIKE 'VSUITE-%' OR payment_id LIKE 'VSUITE-%';

\c paymentx_reconciliation

-- reconciliation_record, mismatch_record and reconciliation_summary all
-- FK-cascade (ON DELETE CASCADE) from reconciliation_batch, so deleting
-- the batch is sufficient - listed explicitly first only for clarity/
-- auditability of what actually gets removed.
DELETE FROM settlement_file      WHERE created_by = 'VALIDATION_SUITE';
DELETE FROM reconciliation_batch WHERE created_by = 'VALIDATION_SUITE';

\c paymentx_reporting

-- report_result, report_metadata and report_export all FK-cascade from
-- report_execution, which itself FK-cascades from report_request - deleting
-- report_request is sufficient; report_execution is also targeted directly
-- in case a run created executions without going through a fresh request.
DELETE FROM report_execution WHERE created_by = 'VALIDATION_SUITE';
DELETE FROM report_request   WHERE requested_by = 'VALIDATION_SUITE';
