/**
 * ENGLISH: The real API client for the Phase 2 PostgreSQL read-only
 * domain. What it does: calls the backend's paginated listing
 * endpoints for all 8 business domains (participants, payments,
 * routing rules, audit events, notifications, reconciliation batches,
 * settlement files, report executions) plus per-database connection/
 * Liquibase status and table info - every interface here mirrors a
 * real backend record exactly (see backend dto/postgres/*.java).
 * There is no function here that could ever send a WHERE clause or
 * raw SQL - only page/size numbers cross the wire. Why it exists: the
 * frontend must never touch Postgres directly - this is the only
 * path. How it will communicate with the backend: real HTTP calls to
 * PostgresController.
 *
 * HINGLISH: Phase 2 ke PostgreSQL read-only domain ke liye real API
 * client. Ye kya karti hai: backend ke saare 8 business domains ke
 * liye paginated listing endpoints call karta hai (participants,
 * payments, routing rules, audit events, notifications, reconciliation
 * batches, settlement files, report executions) plus per-database
 * connection/Liquibase status aur table info - yahan har interface ek
 * real backend record ko exactly mirror karti hai (backend dto/
 * postgres/*.java dekho). Yahan koi function nahi hai jo kabhi WHERE
 * clause ya raw SQL bhej sake - sirf page/size numbers wire ke
 * across jaate hain. Ye dashboard me kyu hai: frontend ko kabhi
 * Postgres ko directly touch nahi karna chahiye - yehi ek raasta hai.
 * Backend se kaise connect hogi: PostgresController ko real HTTP
 * calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

export interface ParticipantSummary {
  id: number
  bankId: string
  legalName: string
  status: string
  onboardedAt: string
  updatedAt: string
}

export interface PaymentSummary {
  id: string
  paymentReference: string
  correlationId: string
  traceId: string
  scheme: string
  paymentType: string
  channel: string
  amount: number
  currency: string
  debtorAccount: string
  debtorParticipantId: string
  creditorAccount: string
  creditorParticipantId: string
  status: string
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

/** Mirrors backend PaymentFilter - every field optional, undefined fields are simply omitted from the query string. */
export interface PaymentFilterParams {
  search?: string
  status?: string
  scheme?: string
  participantId?: string
  dateFrom?: string
  dateTo?: string
  sortField?: 'CREATED_AT' | 'UPDATED_AT' | 'AMOUNT' | 'STATUS' | 'PAYMENT_REFERENCE'
  sortAscending?: boolean
}

export interface PaymentStatsSummary {
  totalPayments: number
  successful: number
  failed: number
  pending: number
  processing: number
  successRatePercent: number
  failureRatePercent: number
  averageLatencyMillis: number | null
  paymentsLastHour: number
  tps: number
}

export type PaymentFlowStageStatus = 'COMPLETED' | 'PROCESSING' | 'FAILED' | 'NOT_STARTED' | 'UNAVAILABLE'

export interface PaymentFlowStage {
  stage: string
  status: PaymentFlowStageStatus
  detail: string | null
  occurredAt: string | null
}

export interface PaymentFlowDetail {
  paymentId: string
  paymentReference: string
  correlationId: string
  traceId: string
  currentStatus: string
  stages: PaymentFlowStage[]
}

export interface RoutingRuleSummary {
  id: string
  scheme: string
  participantId: string | null
  targetRoute: string
  priority: number
  active: boolean
  isDefault: boolean
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface AuditEventSummary {
  id: string
  eventType: string
  eventStatus: string
  sourceService: string
  actorId: string | null
  actorType: string | null
  correlationId: string | null
  traceId: string | null
  paymentId: string | null
  participantId: string | null
  reference: string | null
  occurredAt: string
}

export interface NotificationSummary {
  id: string
  sourceEventType: string
  channel: string
  status: string
  recipient: string
  subject: string | null
  correlationId: string | null
  traceId: string | null
  paymentId: string | null
  participantId: string | null
  retryCount: number
  maxRetries: number
  lastAttemptAt: string | null
  nextRetryAt: string | null
  failureReason: string | null
  createdAt: string
}

export interface ReconciliationBatchSummary {
  id: string
  batchType: string
  status: string
  settlementFileId: string | null
  windowFrom: string | null
  windowTo: string | null
  startedAt: string | null
  completedAt: string | null
  totalRecords: number
  matchedCount: number
  mismatchCount: number
  triggeredBy: string | null
  failureReason: string | null
}

export interface SettlementFileSummary {
  id: string
  fileName: string
  fileType: string
  status: string
  fileSizeBytes: number | null
  recordCount: number | null
  checksumHash: string | null
  storagePath: string | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

export interface ReconciliationRecordSummary {
  id: string
  batchId: string
  paymentReference: string
  participantId: string | null
  internalAmount: number | null
  externalAmount: number | null
  internalCurrency: string | null
  externalCurrency: string | null
  internalStatus: string | null
  externalStatus: string | null
  reconciliationStatus: string
  settlementFileName: string | null
  mismatchReason: string | null
  createdAt: string
}

export interface ReportExecutionSummary {
  id: string
  reportRequestId: string
  status: string
  startedAt: string | null
  completedAt: string | null
  rowCount: number | null
  generationTimeMillis: number | null
  retryCount: number | null
  failureReason: string | null
}

export interface ReportFileSummary {
  exportId: string
  reportType: string
  displayName: string | null
  format: string
  executionStatus: string
  fileSizeBytes: number | null
  downloadCount: number | null
  fileName: string
  createdAt: string
}

export interface PaymentTimeseriesBucket {
  bucketStart: string
  total: number
  successful: number
  failed: number
}

export interface DatabaseStatus {
  databaseName: string
  reachable: boolean
  errorMessage: string | null
  appliedChangesetCount: number | null
  lastChangesetId: string | null
  lastChangesetExecutedAt: string | null
  responseTimeMillis: number
}

export interface TableInfo {
  tableName: string
  rowCount: number
}

async function getPage<T>(path: string, page: number, size: number, params: Record<string, string | undefined> = {}): Promise<PageResponse<T>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<T>>>(path, { params: { page, size, ...params } })
  return response.data.data ?? { content: [], page, size, totalElements: 0 }
}

export const fetchParticipants = (page: number, size: number, search?: string) =>
  getPage<ParticipantSummary>('/api/v1/postgres/participants', page, size, { search })

export const fetchPayments = (page: number, size: number) =>
  getPage<PaymentSummary>('/api/v1/postgres/payments', page, size)

export async function fetchPaymentsFiltered(page: number, size: number, filters: PaymentFilterParams): Promise<PageResponse<PaymentSummary>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<PaymentSummary>>>('/api/v1/postgres/payments', {
    params: { page, size, ...filters },
  })
  return response.data.data ?? { content: [], page, size, totalElements: 0 }
}

export async function fetchPaymentStats(): Promise<PaymentStatsSummary> {
  const response = await axiosClient.get<ApiResponse<PaymentStatsSummary>>('/api/v1/postgres/payments/stats')
  if (!response.data.data) throw new Error('Backend returned an empty payment stats payload.')
  return response.data.data
}

export async function fetchPaymentFlow(reference: string): Promise<PaymentFlowDetail> {
  const response = await axiosClient.get<ApiResponse<PaymentFlowDetail>>(
    `/api/v1/postgres/payments/by-reference/${encodeURIComponent(reference)}/flow`,
  )
  if (!response.data.data) throw new Error('Backend returned an empty payment flow payload.')
  return response.data.data
}

export const fetchRoutingRules = (page: number, size: number, search?: string) =>
  getPage<RoutingRuleSummary>('/api/v1/postgres/routing-rules', page, size, { search })

export const fetchAuditEvents = (page: number, size: number, search?: string) =>
  getPage<AuditEventSummary>('/api/v1/postgres/audit-events', page, size, { search })

export const fetchNotifications = (page: number, size: number, search?: string) =>
  getPage<NotificationSummary>('/api/v1/postgres/notifications', page, size, { search })

export const fetchReconciliationBatches = (page: number, size: number) =>
  getPage<ReconciliationBatchSummary>('/api/v1/postgres/reconciliation-batches', page, size)

export const fetchReconciliationRecords = (page: number, size: number, search?: string, status?: string) =>
  getPage<ReconciliationRecordSummary>('/api/v1/postgres/reconciliation-records', page, size, { search, status })

export const fetchSettlementFiles = (page: number, size: number) =>
  getPage<SettlementFileSummary>('/api/v1/postgres/settlement-files', page, size)

export const fetchReportExecutions = (page: number, size: number) =>
  getPage<ReportExecutionSummary>('/api/v1/postgres/report-executions', page, size)

export const fetchReportFiles = (page: number, size: number) =>
  getPage<ReportFileSummary>('/api/v1/postgres/report-files', page, size)

export async function fetchPaymentTimeseries(windowMinutes: number): Promise<PaymentTimeseriesBucket[]> {
  const response = await axiosClient.get<ApiResponse<PaymentTimeseriesBucket[]>>('/api/v1/postgres/payments/timeseries', {
    params: { windowMinutes },
  })
  return response.data.data ?? []
}

export async function fetchDatabaseStatuses(): Promise<DatabaseStatus[]> {
  const response = await axiosClient.get<ApiResponse<DatabaseStatus[]>>('/api/v1/postgres/databases')
  return response.data.data ?? []
}

export async function fetchTableInfo(databaseSlug: string): Promise<TableInfo[]> {
  const response = await axiosClient.get<ApiResponse<TableInfo[]>>(`/api/v1/postgres/databases/${databaseSlug}/tables`)
  return response.data.data ?? []
}
