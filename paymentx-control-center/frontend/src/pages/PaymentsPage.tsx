/**
 * ENGLISH: The "/payments" Transaction Monitor - real, server-side
 * filtered/searched/sorted/paginated payment records from
 * paymentx_payment.payment. What it does: every control here (search,
 * status, scheme, participant, date range, sort) maps to one real
 * PaymentFilter query param the backend turns into a parameterized
 * WHERE/ORDER BY fragment (see PaymentRepository.findFiltered) -
 * filtering happens on the server, not by hiding rows client-side, so
 * pagination stays correct. Clicking a row navigates to its real
 * Payment Flow. Latency is computed per-row from the real
 * updatedAt-createdAt delta (the same signal PaymentStatsSummary
 * averages on the Home page). Why it exists: this IS the Phase 3
 * Transaction Monitor requirement, replacing Phase 2's plain paginated
 * list. How it will communicate with the backend: via
 * usePaymentsFiltered -> postgresService.ts -> GET
 * /api/v1/postgres/payments (with filter params).
 *
 * HINGLISH: "/payments" Transaction Monitor - real, server-side
 * filtered/searched/sorted/paginated payment records,
 * paymentx_payment.payment se. Ye kya karti hai: yahan har control
 * (search, status, scheme, participant, date range, sort) ek real
 * PaymentFilter query param par map hota hai jise backend ek
 * parameterized WHERE/ORDER BY fragment me badalta hai
 * (PaymentRepository.findFiltered dekho) - filtering server par hoti
 * hai, client-side rows chhupa kar nahi, isliye pagination correct
 * rehta hai. Ek row par click karne se uske real Payment Flow par
 * navigate hota hai. Latency har-row real updatedAt-createdAt delta se
 * compute hoti hai (wahi signal jise PaymentStatsSummary Home page par
 * average karta hai). Ye dashboard me kyu hai: yehi Phase 3
 * Transaction Monitor requirement HAI, Phase 2 ki plain paginated list
 * ki jagah. Backend se kaise connect hogi: usePaymentsFiltered ->
 * postgresService.ts -> GET /api/v1/postgres/payments (filter params
 * ke saath) ke through.
 */
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Chip, Link as MuiLink, MenuItem, TextField, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import { TimeRangeSelector } from '../components/TimeRangeSelector'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { usePaymentsFiltered, useParticipants } from '../hooks/usePostgresData'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import type { PaymentFilterParams, PaymentSummary } from '../services/postgresService'
import type { TimeRange } from '../types/common'

const PAGE_SIZE = 20
const STATUS_OPTIONS = [
    'RECEIVED', 'VALIDATED', 'PROCESSING', 'ROUTING', 'DEBITING', 'DEBIT_SUCCESS', 'DEBIT_FAILED',
    'CREDITING', 'CREDIT_SUCCESS', 'CREDIT_FAILED', 'SETTLING', 'SETTLED', 'RETURNED', 'REVERSED',
    'FAILED', 'CANCELLED', 'TIMEOUT', 'RETRYING',
]
const SCHEME_OPTIONS = ['INSTANT_PAYMENT', 'REAL_TIME_PAYMENT', 'CARD_PAYMENT']
const DATE_RANGES: TimeRange[] = [
  { label: 'All', fromMinutesAgo: 0 },
  { label: '1h', fromMinutesAgo: 60 },
  { label: '24h', fromMinutesAgo: 1440 },
  { label: '7d', fromMinutesAgo: 10_080 },
  { label: '30d', fromMinutesAgo: 43_200 },
]

function latencyLabel(row: PaymentSummary): string {
  const ms = new Date(row.updatedAt).getTime() - new Date(row.createdAt).getTime()
  if (Number.isNaN(ms) || ms < 0) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

export default function PaymentsPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [scheme, setScheme] = useState('')
  const [participantId, setParticipantId] = useState(searchParams.get('participantId') ?? '')
  const [dateRange, setDateRange] = useState<TimeRange>(DATE_RANGES[0])
  const [sortField, setSortField] = useState<NonNullable<PaymentFilterParams['sortField']>>('CREATED_AT')
  const [sortAscending, setSortAscending] = useState(false)

  const participants = useParticipants(0, 50)
  const debouncedSearch = useDebouncedValue(search)

  const filters: PaymentFilterParams = useMemo(() => ({
    search: debouncedSearch.trim() || undefined,
    status: status || undefined,
    scheme: scheme || undefined,
    participantId: participantId || undefined,
    dateFrom: dateRange.fromMinutesAgo > 0 ? new Date(Date.now() - dateRange.fromMinutesAgo * 60_000).toISOString() : undefined,
    sortField,
    sortAscending,
  }), [debouncedSearch, status, scheme, participantId, dateRange, sortField, sortAscending])

  const { data, isLoading, isError, error, refetch } = usePaymentsFiltered(page, PAGE_SIZE, filters)

  const columns: DataTableColumn<PaymentSummary>[] = [
    {
      key: 'reference',
      label: 'Reference',
      render: (row) => (
        <MuiLink
          component="button"
          underline="hover"
          onClick={() => navigate(`/payment-flow?reference=${encodeURIComponent(row.paymentReference)}`)}
        >
          {row.paymentReference}
        </MuiLink>
      ),
    },
    { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
    { key: 'amount', label: 'Amount', align: 'right', render: (row) => row.amount.toFixed(2) },
    { key: 'currency', label: 'Currency', render: (row) => row.currency },
    { key: 'participant', label: 'Participant', render: (row) => `${row.debtorParticipantId} → ${row.creditorParticipantId}` },
    { key: 'scheme', label: 'Scheme', render: (row) => row.scheme },
    { key: 'correlationId', label: 'Correlation ID', render: (row) => row.correlationId },
    { key: 'traceId', label: 'Trace ID', render: (row) => row.traceId },
    { key: 'createdAt', label: 'Created At', render: (row) => formatTimestamp(row.createdAt) },
    { key: 'updatedAt', label: 'Updated At', render: (row) => formatTimestamp(row.updatedAt) },
    { key: 'latency', label: 'Latency', align: 'right', render: (row) => latencyLabel(row) },
    { key: 'failureReason', label: 'Failure Reason', render: (row) => row.failureReason ?? '—' },
  ]

  return (
    <PageContainer>
      <PageHeader title="Transaction Monitor" description="Real, server-side filtered payment records from paymentx_payment.payment." />

      <FilterBar>
        <SearchBar value={search} onChange={setSearch} placeholder="Reference / correlation / trace ID…" />
        <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">All statuses</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Scheme" value={scheme} onChange={(e) => setScheme(e.target.value)} sx={{ minWidth: 170 }}>
          <MenuItem value="">All schemes</MenuItem>
          {SCHEME_OPTIONS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Participant" value={participantId} onChange={(e) => setParticipantId(e.target.value)} sx={{ minWidth: 200 }}>
          <MenuItem value="">All participants</MenuItem>
          {participants.data?.content.map((p) => (
            <MenuItem key={p.bankId} value={p.bankId}>{p.legalName} ({p.bankId})</MenuItem>
          ))}
        </TextField>
        <TimeRangeSelector value={dateRange} onChange={setDateRange} ranges={DATE_RANGES} />
        <TextField select size="small" label="Sort by" value={sortField} onChange={(e) => setSortField(e.target.value as NonNullable<PaymentFilterParams['sortField']>)} sx={{ minWidth: 150 }}>
          <MenuItem value="CREATED_AT">Created</MenuItem>
          <MenuItem value="UPDATED_AT">Updated</MenuItem>
          <MenuItem value="AMOUNT">Amount</MenuItem>
          <MenuItem value="STATUS">Status</MenuItem>
          <MenuItem value="PAYMENT_REFERENCE">Reference</MenuItem>
        </TextField>
        <Chip
          label={sortAscending ? 'Ascending' : 'Descending'}
          onClick={() => setSortAscending((v) => !v)}
          variant="outlined"
        />
      </FilterBar>

      {isLoading && <LoadingState message="Loading transactions…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <Typography variant="caption" color="text.secondary">
            {data.totalElements} matching transaction(s)
          </Typography>
          <DataTable
            rows={data.content}
            columns={columns}
            getRowKey={(row) => row.id}
            emptyTitle="No matching transactions"
            emptyMessage="No payments match the current search/filter criteria."
          />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </PageContainer>
  )
}
