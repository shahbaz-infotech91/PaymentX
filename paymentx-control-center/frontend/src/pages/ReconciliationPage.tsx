/**
 * ENGLISH: The "/reconciliation" page - real reconciliation batches
 * (paymentx_reconciliation.reconciliation_batch, with real matched/
 * mismatch counts) and real settlement files
 * (paymentx_reconciliation.settlement_file), switchable via tabs, both
 * via the backend's read-only Postgres layer. Why it exists: required
 * route "/reconciliation", now wired to the real Phase 2 backend. How
 * it will communicate with the backend: via useReconciliationBatches/
 * useSettlementFiles -> postgresService.ts -> GET
 * /api/v1/postgres/reconciliation-batches and /settlement-files.
 *
 * HINGLISH: "/reconciliation" page - real reconciliation batches
 * (paymentx_reconciliation.reconciliation_batch, real matched/
 * mismatch counts ke saath) aur real settlement files
 * (paymentx_reconciliation.settlement_file), tabs se switchable, dono
 * backend ke read-only Postgres layer ke through. Ye dashboard me kyu
 * hai: required route "/reconciliation", ab real Phase 2 backend se
 * wired hai. Backend se kaise connect hogi:
 * useReconciliationBatches/useSettlementFiles -> postgresService.ts
 * -> GET /api/v1/postgres/reconciliation-batches aur
 * /settlement-files ke through.
 */
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Tabs, Tab, Chip, MenuItem, TextField } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import { useReconciliationBatches, useReconciliationRecords, useSettlementFiles } from '../hooks/usePostgresData'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp, formatBytes } from '../utils/formatters'
import type { ReconciliationBatchSummary, ReconciliationRecordSummary, SettlementFileSummary } from '../services/postgresService'

const PAGE_SIZE = 20

/** The real ReconciliationStatus enum paymentx-reconciliation-service's entity.ReconciliationStatus.java defines - verified live against the actual source, not the Phase 4 brief's generic wording (this platform's real categories are match/mismatch-shaped, not a generic pending/processing/completed/failed lifecycle). */
const RECORD_STATUSES = [
  'MATCHED', 'MISSING', 'DUPLICATE', 'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH',
  'STATUS_MISMATCH', 'SETTLEMENT_DELAY', 'LATE_SETTLEMENT', 'ORPHAN', 'UNEXPECTED_SETTLEMENT',
]

const batchColumns: DataTableColumn<ReconciliationBatchSummary>[] = [
  { key: 'batchType', label: 'Batch Type', render: (row) => row.batchType },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'total', label: 'Total', align: 'right', render: (row) => row.totalRecords },
  { key: 'matched', label: 'Matched', align: 'right', render: (row) => row.matchedCount },
  { key: 'mismatch', label: 'Mismatch', align: 'right', render: (row) => row.mismatchCount },
  { key: 'startedAt', label: 'Started', render: (row) => formatTimestamp(row.startedAt) },
  { key: 'completedAt', label: 'Completed', render: (row) => formatTimestamp(row.completedAt) },
]

const settlementColumns: DataTableColumn<SettlementFileSummary>[] = [
  { key: 'fileName', label: 'File Name', render: (row) => row.fileName },
  { key: 'fileType', label: 'Type', render: (row) => row.fileType },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'size', label: 'Size', align: 'right', render: (row) => formatBytes(row.fileSizeBytes) },
  { key: 'records', label: 'Records', align: 'right', render: (row) => row.recordCount ?? '—' },
  { key: 'createdAt', label: 'Created', render: (row) => formatTimestamp(row.createdAt) },
]

function BatchesTab() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useReconciliationBatches(page, PAGE_SIZE)
  if (isLoading) return <LoadingState message="Loading reconciliation batches…" />
  if (isError) return <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />
  if (!data) return null
  return (
    <>
      <DataTable
        rows={data.content}
        columns={batchColumns}
        getRowKey={(row) => row.id}
        emptyTitle="No reconciliation batches yet"
        emptyMessage="No records exist in paymentx_reconciliation.reconciliation_batch yet."
      />
      <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
    </>
  )
}

const recordColumns: DataTableColumn<ReconciliationRecordSummary>[] = [
  { key: 'paymentReference', label: 'Payment Reference', render: (row) => row.paymentReference },
  { key: 'settlementFileName', label: 'Settlement Reference', render: (row) => row.settlementFileName ?? '—' },
  { key: 'internalAmount', label: 'Amount', align: 'right', render: (row) => (row.internalAmount != null ? row.internalAmount.toLocaleString() : '—') },
  { key: 'internalCurrency', label: 'Currency', render: (row) => row.internalCurrency ?? '—' },
  { key: 'reconciliationStatus', label: 'Status', render: (row) => <Chip size="small" label={row.reconciliationStatus} /> },
  { key: 'mismatchReason', label: 'Mismatch Reason', render: (row) => row.mismatchReason ?? '—' },
  { key: 'createdAt', label: 'Timestamp', render: (row) => formatTimestamp(row.createdAt) },
]

function RecordsTab() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const debouncedSearch = useDebouncedValue(search)
  const { data, isLoading, isError, error, refetch } = useReconciliationRecords(page, PAGE_SIZE, debouncedSearch, status)
  return (
    <>
      <FilterBar>
        <SearchBar
          value={search}
          onChange={(value) => {
            setSearch(value)
            setPage(0)
          }}
          placeholder="Search payment reference, participant…"
        />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value)
            setPage(0)
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {RECORD_STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </TextField>
      </FilterBar>
      {isLoading && <LoadingState message="Loading reconciliation records…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <DataTable
            rows={data.content}
            columns={recordColumns}
            getRowKey={(row) => row.id}
            emptyTitle="No reconciliation records"
            emptyMessage="No records match this filter in paymentx_reconciliation.reconciliation_record."
          />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </>
  )
}

function SettlementFilesTab() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useSettlementFiles(page, PAGE_SIZE)
  if (isLoading) return <LoadingState message="Loading settlement files…" />
  if (isError) return <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />
  if (!data) return null
  return (
    <>
      <DataTable
        rows={data.content}
        columns={settlementColumns}
        getRowKey={(row) => row.id}
        emptyTitle="No settlement files yet"
        emptyMessage="No records exist in paymentx_reconciliation.settlement_file yet."
      />
      <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
    </>
  )
}

export default function ReconciliationPage() {
  const [searchParams] = useSearchParams()
  const initialTab = searchParams.get('tab') === 'settlement' ? 2 : searchParams.get('tab') === 'records' ? 1 : 0
  const [tab, setTab] = useState(initialTab)
  return (
    <PageContainer>
      <PageHeader title="Reconciliation" description="Real reconciliation batches, per-payment match/mismatch records, and settlement files." />
      <Tabs value={tab} onChange={(_, value) => setTab(value)}>
        <Tab label="Batches" />
        <Tab label="Records" />
        <Tab label="Settlement Files" />
      </Tabs>
      {tab === 0 && <BatchesTab />}
      {tab === 1 && <RecordsTab />}
      {tab === 2 && <SettlementFilesTab />}
    </PageContainer>
  )
}
