/**
 * ENGLISH: React Query hooks wrapping every postgresService.ts call.
 * What it does: one hook per business domain, each keyed by
 * [domain, page, size] so switching pages never serves stale data
 * from a different page number, plus two hooks for the database
 * status/table-info sidebar. Why it exists: keeps Payments/Audit/
 * Notifications/Reconciliation/Reporting/Database pages free of axios
 * calls.
 *
 * HINGLISH: Har postgresService.ts call ko wrap karne wale React
 * Query hooks. Ye kya karti hai: har business domain ke liye ek hook,
 * har ek [domain, page, size] se keyed hai taaki page switch karne par
 * kabhi doosre page number ka stale data serve na ho, plus database
 * status/table-info sidebar ke liye do hooks. Ye dashboard me kyu hai:
 * Payments/Audit/Notifications/Reconciliation/Reporting/Database
 * pages ko axios calls se free rakhta hai.
 */
import { useQuery } from '@tanstack/react-query'
import {
  fetchParticipants,
  fetchPayments,
  fetchPaymentsFiltered,
  fetchPaymentStats,
  fetchPaymentFlow,
  fetchPaymentTimeseries,
  fetchRoutingRules,
  fetchAuditEvents,
  fetchNotifications,
  fetchReconciliationBatches,
  fetchReconciliationRecords,
  fetchSettlementFiles,
  fetchReportExecutions,
  fetchReportFiles,
  fetchDatabaseStatuses,
  fetchTableInfo,
  type PaymentFilterParams,
} from '../services/postgresService'

export function useParticipants(page: number, size: number, search = '') {
  return useQuery({ queryKey: ['participants', page, size, search], queryFn: () => fetchParticipants(page, size, search || undefined) })
}

export function usePayments(page: number, size: number) {
  return useQuery({ queryKey: ['payments', page, size], queryFn: () => fetchPayments(page, size) })
}

/**
 * Transaction Monitor's real, filtered/searched/sorted page - polled
 * every 20s (the "transactions" tier of Phase 3's tiered polling
 * policy), distinct from the 30s "health" tier and the on-demand
 * "alerts"/"metrics" tiers.
 */
export function usePaymentsFiltered(page: number, size: number, filters: PaymentFilterParams) {
  return useQuery({
    queryKey: ['payments-filtered', page, size, filters],
    queryFn: () => fetchPaymentsFiltered(page, size, filters),
    refetchInterval: 20_000,
  })
}

export function usePaymentStats() {
  return useQuery({ queryKey: ['payment-stats'], queryFn: fetchPaymentStats, refetchInterval: 30_000 })
}

export function usePaymentFlow(reference: string | null) {
  return useQuery({
    queryKey: ['payment-flow', reference],
    queryFn: () => fetchPaymentFlow(reference as string),
    enabled: reference !== null && reference.trim().length > 0,
  })
}

export function useRoutingRules(page: number, size: number, search = '') {
  return useQuery({ queryKey: ['routing-rules', page, size, search], queryFn: () => fetchRoutingRules(page, size, search || undefined) })
}

export function useAuditEvents(page: number, size: number, search = '') {
  return useQuery({ queryKey: ['audit-events', page, size, search], queryFn: () => fetchAuditEvents(page, size, search || undefined) })
}

export function useNotifications(page: number, size: number, search = '') {
  return useQuery({ queryKey: ['notifications', page, size, search], queryFn: () => fetchNotifications(page, size, search || undefined) })
}

export function useReconciliationBatches(page: number, size: number) {
  return useQuery({ queryKey: ['reconciliation-batches', page, size], queryFn: () => fetchReconciliationBatches(page, size) })
}

export function useReconciliationRecords(page: number, size: number, search = '', status = '') {
  return useQuery({
    queryKey: ['reconciliation-records', page, size, search, status],
    queryFn: () => fetchReconciliationRecords(page, size, search || undefined, status || undefined),
  })
}

export function useSettlementFiles(page: number, size: number) {
  return useQuery({ queryKey: ['settlement-files', page, size], queryFn: () => fetchSettlementFiles(page, size) })
}

export function useReportExecutions(page: number, size: number) {
  return useQuery({ queryKey: ['report-executions', page, size], queryFn: () => fetchReportExecutions(page, size) })
}

export function useReportFiles(page: number, size: number) {
  return useQuery({ queryKey: ['report-files', page, size], queryFn: () => fetchReportFiles(page, size) })
}

export function usePaymentTimeseries(windowMinutes: number) {
  return useQuery({
    queryKey: ['payment-timeseries', windowMinutes],
    queryFn: () => fetchPaymentTimeseries(windowMinutes),
    refetchInterval: 30_000,
  })
}

export function useDatabaseStatuses() {
  return useQuery({ queryKey: ['database-statuses'], queryFn: fetchDatabaseStatuses })
}

export function useTableInfo(databaseSlug: string | null) {
  return useQuery({
    queryKey: ['database-tables', databaseSlug],
    queryFn: () => fetchTableInfo(databaseSlug as string),
    enabled: databaseSlug !== null,
  })
}
