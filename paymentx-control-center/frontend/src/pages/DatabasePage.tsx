/**
 * ENGLISH: The "/database" page - real per-database connection status
 * (Liquibase changelog state, live JDBC reachability) PLUS the Phase 4
 * read-only database viewer: one tab per real business domain
 * (Participants, Payments, Routing Rules, Audit, Notifications,
 * Reconciliation, Reports, Settlement), each backed by the backend's
 * real, paginated, read-only Postgres endpoint (see
 * PostgresController.java - there is no PUT/POST/DELETE anywhere on
 * that controller, and no endpoint accepts raw SQL). What it does:
 * every tab gets real search (where the backend supports it) and real
 * pagination; column headers double as this table's honest schema
 * summary. Why it exists: required Phase 4 "read-only database
 * viewer" module - reuses the exact same real hooks/endpoints the
 * dedicated Payments/Audit/Notifications/Reconciliation/Reporting
 * pages use, so there is exactly one real code path per domain, not
 * two. How it will communicate with the backend: via
 * useDatabaseStatuses/useTableInfo/useParticipants/usePaymentsFiltered/
 * useRoutingRules/useAuditEvents/useNotifications/
 * useReconciliationRecords/useReportExecutions/useSettlementFiles ->
 * postgresService.ts -> GET /api/v1/postgres/*.
 *
 * HINGLISH: "/database" page - real per-database connection status
 * (Liquibase changelog state, live JDBC reachability) PLUS Phase 4 ka
 * read-only database viewer: har real business domain ke liye ek tab
 * (Participants, Payments, Routing Rules, Audit, Notifications,
 * Reconciliation, Reports, Settlement), har ek backend ke real,
 * paginated, read-only Postgres endpoint se backed (PostgresController.
 * java dekho - us controller par kahin bhi PUT/POST/DELETE nahi hai,
 * aur koi endpoint raw SQL accept nahi karta). Ye kya karti hai: har
 * tab ko real search (jahan backend support karta hai) aur real
 * pagination milta hai; column headers hi is table ke honest schema
 * summary ka kaam karte hain. Ye dashboard me kyu hai: required Phase
 * 4 "read-only database viewer" module - wahi real hooks/endpoints
 * reuse karta hai jo dedicated Payments/Audit/Notifications/
 * Reconciliation/Reporting pages use karte hain, taaki har domain ke
 * liye exactly ek real code path ho, do nahi. Backend se kaise connect
 * hogi: useDatabaseStatuses/useTableInfo/useParticipants/
 * usePaymentsFiltered/useRoutingRules/useAuditEvents/useNotifications/
 * useReconciliationRecords/useReportExecutions/useSettlementFiles ->
 * postgresService.ts -> GET /api/v1/postgres/* ke through.
 */
import { useState } from 'react'
import { Card, CardActionArea, CardContent, Chip, Grid2 as Grid, MenuItem, Stack, Tab, Tabs, TextField, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import {
  useDatabaseStatuses,
  useTableInfo,
  useParticipants,
  usePaymentsFiltered,
  useRoutingRules,
  useAuditEvents,
  useNotifications,
  useReconciliationRecords,
  useReportExecutions,
  useSettlementFiles,
} from '../hooks/usePostgresData'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp, formatBytes } from '../utils/formatters'
import type {
  ParticipantSummary,
  PaymentSummary,
  RoutingRuleSummary,
  AuditEventSummary,
  NotificationSummary,
  ReconciliationRecordSummary,
  ReportExecutionSummary,
  SettlementFileSummary,
} from '../services/postgresService'

const DATABASE_SLUGS = ['validation', 'payment', 'routing', 'audit', 'notification', 'reconciliation', 'reporting'] as const
const PAGE_SIZE = 20

const tableColumns: DataTableColumn<{ tableName: string; rowCount: number }>[] = [
  { key: 'tableName', label: 'Table', render: (row) => row.tableName },
  { key: 'rowCount', label: 'Row Count', align: 'right', render: (row) => row.rowCount.toLocaleString() },
]

interface PagedQueryResult<T> {
  data?: { content: T[]; page: number; size: number; totalElements: number }
  isLoading: boolean
  isError: boolean
  error: unknown
  refetch: () => void
}

/** Generic "search + paginated table" tab body shared by every domain below - real data in, real search/pagination out, nothing invented. */
function DbTab<T>({
  columns,
  getRowKey,
  useData,
  searchPlaceholder,
  emptyTitle,
  emptyMessage,
}: {
  columns: DataTableColumn<T>[]
  getRowKey: (row: T) => string
  useData: (page: number, size: number, search: string) => PagedQueryResult<T>
  searchPlaceholder: string
  emptyTitle: string
  emptyMessage: string
}) {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search)
  const { data, isLoading, isError, error, refetch } = useData(page, PAGE_SIZE, debouncedSearch)
  return (
    <>
      <FilterBar>
        <SearchBar
          value={search}
          onChange={(value) => {
            setSearch(value)
            setPage(0)
          }}
          placeholder={searchPlaceholder}
        />
      </FilterBar>
      {isLoading && <LoadingState message="Loading…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <DataTable rows={data.content} columns={columns} getRowKey={getRowKey} emptyTitle={emptyTitle} emptyMessage={emptyMessage} />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </>
  )
}

const participantColumns: DataTableColumn<ParticipantSummary>[] = [
  { key: 'bankId', label: 'Bank ID', render: (row) => row.bankId },
  { key: 'legalName', label: 'Legal Name', render: (row) => row.legalName },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'onboardedAt', label: 'Onboarded', render: (row) => formatTimestamp(row.onboardedAt) },
  { key: 'updatedAt', label: 'Updated', render: (row) => formatTimestamp(row.updatedAt) },
]

const paymentColumns: DataTableColumn<PaymentSummary>[] = [
  { key: 'paymentReference', label: 'Payment Reference', render: (row) => row.paymentReference },
  { key: 'scheme', label: 'Scheme', render: (row) => row.scheme },
  { key: 'amount', label: 'Amount', align: 'right', render: (row) => `${row.amount.toLocaleString()} ${row.currency}` },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'createdAt', label: 'Created', render: (row) => formatTimestamp(row.createdAt) },
]

const routingRuleColumns: DataTableColumn<RoutingRuleSummary>[] = [
  { key: 'scheme', label: 'Scheme', render: (row) => row.scheme },
  { key: 'participantId', label: 'Participant', render: (row) => row.participantId ?? 'default' },
  { key: 'targetRoute', label: 'Target Route', render: (row) => row.targetRoute },
  { key: 'priority', label: 'Priority', align: 'right', render: (row) => row.priority },
  { key: 'active', label: 'Active', render: (row) => <StatusBadge status={row.active ? 'UP' : 'DOWN'} /> },
]

const auditColumns: DataTableColumn<AuditEventSummary>[] = [
  { key: 'eventType', label: 'Event Type', render: (row) => row.eventType },
  { key: 'eventStatus', label: 'Status', render: (row) => <Chip size="small" label={row.eventStatus} /> },
  { key: 'sourceService', label: 'Source', render: (row) => row.sourceService },
  { key: 'reference', label: 'Payment Reference', render: (row) => row.reference ?? '—' },
  { key: 'occurredAt', label: 'Timestamp', render: (row) => formatTimestamp(row.occurredAt) },
]

const notificationColumns: DataTableColumn<NotificationSummary>[] = [
  { key: 'channel', label: 'Channel', render: (row) => row.channel },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'recipient', label: 'Recipient', render: (row) => row.recipient },
  { key: 'createdAt', label: 'Created', render: (row) => formatTimestamp(row.createdAt) },
]

const reconciliationColumns: DataTableColumn<ReconciliationRecordSummary>[] = [
  { key: 'paymentReference', label: 'Payment Reference', render: (row) => row.paymentReference },
  { key: 'settlementFileName', label: 'Settlement Reference', render: (row) => row.settlementFileName ?? '—' },
  { key: 'reconciliationStatus', label: 'Status', render: (row) => <Chip size="small" label={row.reconciliationStatus} /> },
  { key: 'mismatchReason', label: 'Mismatch Reason', render: (row) => row.mismatchReason ?? '—' },
  { key: 'createdAt', label: 'Timestamp', render: (row) => formatTimestamp(row.createdAt) },
]

const reportColumns: DataTableColumn<ReportExecutionSummary>[] = [
  { key: 'reportRequestId', label: 'Report Request', render: (row) => row.reportRequestId },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'rowCount', label: 'Rows', align: 'right', render: (row) => row.rowCount ?? '—' },
  { key: 'startedAt', label: 'Started', render: (row) => formatTimestamp(row.startedAt) },
]

const settlementColumns: DataTableColumn<SettlementFileSummary>[] = [
  { key: 'fileName', label: 'File Name', render: (row) => row.fileName },
  { key: 'fileType', label: 'Type', render: (row) => row.fileType },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'fileSizeBytes', label: 'Size', align: 'right', render: (row) => formatBytes(row.fileSizeBytes) },
  { key: 'createdAt', label: 'Created', render: (row) => formatTimestamp(row.createdAt) },
]

const TABS = ['Participants', 'Payments', 'Routing Rules', 'Audit', 'Notifications', 'Reconciliation', 'Reports', 'Settlement'] as const

const PAYMENT_SORT_FIELDS: { value: 'CREATED_AT' | 'UPDATED_AT' | 'AMOUNT' | 'STATUS' | 'PAYMENT_REFERENCE'; label: string }[] = [
  { value: 'CREATED_AT', label: 'Created' },
  { value: 'UPDATED_AT', label: 'Updated' },
  { value: 'AMOUNT', label: 'Amount' },
  { value: 'STATUS', label: 'Status' },
  { value: 'PAYMENT_REFERENCE', label: 'Payment Reference' },
]

function PaymentsTab() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [sortField, setSortField] = useState<(typeof PAYMENT_SORT_FIELDS)[number]['value']>('CREATED_AT')
  const [sortAscending, setSortAscending] = useState(false)
  const debouncedSearch = useDebouncedValue(search)
  const { data, isLoading, isError, error, refetch } = usePaymentsFiltered(page, PAGE_SIZE, { search: debouncedSearch || undefined, sortField, sortAscending })
  return (
    <>
      <FilterBar>
        <SearchBar
          value={search}
          onChange={(value) => {
            setSearch(value)
            setPage(0)
          }}
          placeholder="Search payment reference, correlation ID, trace ID…"
        />
        <TextField select size="small" label="Sort by" value={sortField} onChange={(e) => setSortField(e.target.value as typeof sortField)} sx={{ minWidth: 170 }}>
          {PAYMENT_SORT_FIELDS.map((f) => (
            <MenuItem key={f.value} value={f.value}>
              {f.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Direction" value={sortAscending ? 'asc' : 'desc'} onChange={(e) => setSortAscending(e.target.value === 'asc')} sx={{ minWidth: 130 }}>
          <MenuItem value="desc">Descending</MenuItem>
          <MenuItem value="asc">Ascending</MenuItem>
        </TextField>
      </FilterBar>
      {isLoading && <LoadingState message="Loading…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <DataTable rows={data.content} columns={paymentColumns} getRowKey={(row) => row.id} emptyTitle="No payments" emptyMessage="No records match this search." />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </>
  )
}

/** Reconciliation/Reports/Settlement have no real backend search endpoint - real pagination only, no invented client-side filter over an unpaginated set. */
function PaginatedOnlyTab<T>({
  columns,
  getRowKey,
  useData,
  emptyTitle,
  emptyMessage,
}: {
  columns: DataTableColumn<T>[]
  getRowKey: (row: T) => string
  useData: (page: number, size: number) => PagedQueryResult<T>
  emptyTitle: string
  emptyMessage: string
}) {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useData(page, PAGE_SIZE)
  return (
    <>
      {isLoading && <LoadingState message="Loading…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <DataTable rows={data.content} columns={columns} getRowKey={getRowKey} emptyTitle={emptyTitle} emptyMessage={emptyMessage} />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </>
  )
}

export default function DatabasePage() {
  const [selected, setSelected] = useState<string | null>(null)
  const { data: statuses, isLoading, isError, error, refetch } = useDatabaseStatuses()
  const tables = useTableInfo(selected)
  const [tab, setTab] = useState<number>(0)

  return (
    <PageContainer>
      <PageHeader title="Database" description="Real connection status, Liquibase status, and a read-only viewer across all 7 PaymentX databases. No arbitrary SQL, no UPDATE, no DELETE." />
      {isLoading && <LoadingState message="Checking database connections…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {statuses && (
        <Grid container spacing={2}>
          {DATABASE_SLUGS.map((slug, index) => {
            const status = statuses[index]
            return (
              <Grid key={slug} size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant={selected === slug ? 'elevation' : 'outlined'}>
                  <CardActionArea onClick={() => setSelected(slug === selected ? null : slug)}>
                    <CardContent>
                      <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Typography variant="subtitle1" fontWeight={600}>
                          {status?.databaseName ?? slug}
                        </Typography>
                        <StatusBadge status={status?.reachable ? 'UP' : 'DOWN'} />
                      </Stack>
                      <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>
                        {status?.appliedChangesetCount ?? 0} changesets applied
                      </Typography>
                      {status?.lastChangesetExecutedAt && (
                        <Typography variant="caption" color="text.secondary" component="div">
                          Last: {formatTimestamp(status.lastChangesetExecutedAt)}
                        </Typography>
                      )}
                      {status?.errorMessage && (
                        <Typography variant="caption" color="error" component="div" sx={{ mt: 1 }}>
                          {status.errorMessage}
                        </Typography>
                      )}
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            )
          })}
        </Grid>
      )}

      {selected && (
        <>
          <Typography variant="h2" component="h2">
            Tables in {selected}
          </Typography>
          {tables.isLoading && <LoadingState message="Loading table info…" />}
          {tables.isError && <ErrorState message={toApiError(tables.error).message} onRetry={() => tables.refetch()} />}
          {tables.data && (
            <DataTable
              rows={tables.data}
              columns={tableColumns}
              getRowKey={(row) => row.tableName}
              emptyTitle="No tables found"
              emptyMessage="information_schema reported no tables in the public schema."
            />
          )}
        </>
      )}

      <Typography variant="h2" component="h2">
        Data Viewer
      </Typography>
      <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable">
        {TABS.map((label) => (
          <Tab key={label} label={label} />
        ))}
      </Tabs>
      {tab === 0 && (
        <DbTab columns={participantColumns} getRowKey={(row) => String(row.id)} useData={useParticipants} searchPlaceholder="Search bank ID, legal name…" emptyTitle="No participants" emptyMessage="No records exist in paymentx_validation.participant yet." />
      )}
      {tab === 1 && <PaymentsTab />}
      {tab === 2 && (
        <DbTab columns={routingRuleColumns} getRowKey={(row) => row.id} useData={useRoutingRules} searchPlaceholder="Search scheme, target route…" emptyTitle="No routing rules" emptyMessage="No records exist in paymentx_routing.routing_rule yet." />
      )}
      {tab === 3 && (
        <DbTab columns={auditColumns} getRowKey={(row) => row.id} useData={useAuditEvents} searchPlaceholder="Search event type, source, actor, reference…" emptyTitle="No audit events" emptyMessage="No records exist in paymentx_audit.audit_event yet." />
      )}
      {tab === 4 && (
        <DbTab columns={notificationColumns} getRowKey={(row) => row.id} useData={useNotifications} searchPlaceholder="Search recipient, subject…" emptyTitle="No notifications" emptyMessage="No records exist in paymentx_notification.notification yet." />
      )}
      {tab === 5 && (
        <DbTab columns={reconciliationColumns} getRowKey={(row) => row.id} useData={useReconciliationRecords} searchPlaceholder="Search payment reference, participant…" emptyTitle="No reconciliation records" emptyMessage="No records exist in paymentx_reconciliation.reconciliation_record yet." />
      )}
      {tab === 6 && (
        <PaginatedOnlyTab columns={reportColumns} getRowKey={(row) => row.id} useData={useReportExecutions} emptyTitle="No report executions" emptyMessage="No records exist in paymentx_reporting.report_execution yet." />
      )}
      {tab === 7 && (
        <PaginatedOnlyTab columns={settlementColumns} getRowKey={(row) => row.id} useData={useSettlementFiles} emptyTitle="No settlement files" emptyMessage="No records exist in paymentx_reconciliation.settlement_file yet." />
      )}
    </PageContainer>
  )
}
