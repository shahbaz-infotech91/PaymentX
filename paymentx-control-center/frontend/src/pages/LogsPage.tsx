/**
 * ENGLISH: The "/logs" page - a real, bounded log viewer over the
 * real, redirected stdout of every real PaymentX service
 * (paymentx-validation-output/svc-&lt;service&gt;.log, the same files
 * paymentx-validation-suite/scripts/start-all.ps1 writes when starting
 * the real platform). What it does: every filter below
 * (service/level/correlation ID/trace ID/payment reference/time range/
 * search) is sent straight to LogsController's real query - never
 * client-invented data - and the result is always capped at `limit`
 * (max 1000), matching the Phase 4 brief's explicit "do not load
 * unlimited logs" requirement. Why it exists: required Phase 4 "Log
 * Viewer" module. How it will communicate with the backend: via
 * useLogServices/useLogs -> logsService.ts -> GET /api/v1/logs[/services].
 *
 * HINGLISH: "/logs" page - har real PaymentX service ke real,
 * redirected stdout par ek real, bounded log viewer
 * (paymentx-validation-output/svc-&lt;service&gt;.log, wahi files jo
 * paymentx-validation-suite/scripts/start-all.ps1 real platform start
 * karte waqt likhta hai). Ye kya karti hai: neeche ka har filter
 * (service/level/correlation ID/trace ID/payment reference/time
 * range/search) seedha LogsController ki real query ko bheja jaata hai
 * - kabhi client-invented data nahi - aur result hamesha `limit` (max
 * 1000) tak capped hota hai, Phase 4 brief ke explicit "unlimited logs
 * load mat karo" requirement se match karte hue. Ye dashboard me kyu
 * hai: required Phase 4 "Log Viewer" module. Backend se kaise connect
 * hogi: useLogServices/useLogs -> logsService.ts -> GET
 * /api/v1/logs[/services] ke through.
 */
import { useMemo, useState } from 'react'
import { Chip, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import { TimeRangeSelector } from '../components/TimeRangeSelector'
import { useLogServices, useLogs } from '../hooks/useLogs'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import type { LogEntry } from '../services/logsService'
import type { TimeRange } from '../types/common'

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']
const LOG_TIME_RANGES: TimeRange[] = [
  { label: '15m', fromMinutesAgo: 15 },
  { label: '1h', fromMinutesAgo: 60 },
  { label: '6h', fromMinutesAgo: 360 },
  { label: '24h', fromMinutesAgo: 1440 },
  { label: 'All', fromMinutesAgo: 0 },
]
const LIMITS = [100, 200, 500, 1000]

const levelColor: Record<string, 'default' | 'info' | 'warning' | 'error'> = {
  ERROR: 'error',
  WARN: 'warning',
  INFO: 'info',
  DEBUG: 'default',
  TRACE: 'default',
  UNKNOWN: 'default',
}

const columns: DataTableColumn<LogEntry>[] = [
  { key: 'timestamp', label: 'Timestamp', render: (row) => formatTimestamp(row.timestamp) },
  { key: 'service', label: 'Service', render: (row) => row.service },
  { key: 'level', label: 'Level', render: (row) => <Chip size="small" color={levelColor[row.level] ?? 'default'} label={row.level} /> },
  { key: 'correlationId', label: 'Correlation ID', render: (row) => row.correlationId ?? '—' },
  { key: 'traceId', label: 'Trace ID', render: (row) => row.traceId ?? '—' },
  { key: 'paymentReference', label: 'Payment Reference', render: (row) => row.paymentReference ?? '—' },
  {
    key: 'message',
    label: 'Message',
    render: (row) => (
      <Typography variant="body2" component="pre" sx={{ m: 0, whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: '0.75rem' }}>
        {row.logger ? `${row.logger}: ` : ''}
        {row.message}
      </Typography>
    ),
  },
]

export default function LogsPage() {
  const services = useLogServices()
  const [service, setService] = useState('')
  const [level, setLevel] = useState('')
  const [correlationId, setCorrelationId] = useState('')
  const [traceId, setTraceId] = useState('')
  const [paymentReference, setPaymentReference] = useState('')
  const [search, setSearch] = useState('')
  const [range, setRange] = useState<TimeRange>(LOG_TIME_RANGES[1])
  const [limit, setLimit] = useState(200)

  const from = useMemo(
    () => (range.fromMinutesAgo > 0 ? new Date(Date.now() - range.fromMinutesAgo * 60_000).toISOString() : undefined),
    [range],
  )

  // ENGLISH: Free-text filters are debounced before feeding the real backend query, so bounded log scans
  // don't fire on every keystroke - the Service/Level/time-range dropdowns above take effect immediately.
  // HINGLISH: Free-text filters ko real backend query feed karne se pehle debounce kiya jaata hai, taaki
  // bounded log scans har keystroke par fire na hon - upar ke Service/Level/time-range dropdowns turant apply hote hain.
  const debouncedCorrelationId = useDebouncedValue(correlationId)
  const debouncedTraceId = useDebouncedValue(traceId)
  const debouncedPaymentReference = useDebouncedValue(paymentReference)
  const debouncedSearch = useDebouncedValue(search)

  const { data, isLoading, isError, error, refetch } = useLogs({
    service: service || undefined,
    level: level || undefined,
    correlationId: debouncedCorrelationId || undefined,
    traceId: debouncedTraceId || undefined,
    paymentReference: debouncedPaymentReference || undefined,
    search: debouncedSearch || undefined,
    from,
    limit,
  })

  return (
    <PageContainer>
      <PageHeader
        title="Logs"
        description="Real, bounded service logs read from each PaymentX service's real redirected stdout. Results are always capped - this never loads an unlimited log."
      />

      <FilterBar>
        <TextField select size="small" label="Service" value={service} onChange={(e) => setService(e.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="">All services</MenuItem>
          {(services.data ?? []).map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Level" value={level} onChange={(e) => setLevel(e.target.value)} sx={{ minWidth: 140 }}>
          <MenuItem value="">All levels</MenuItem>
          {LEVELS.map((l) => (
            <MenuItem key={l} value={l}>
              {l}
            </MenuItem>
          ))}
        </TextField>
        <TimeRangeSelector value={range} onChange={setRange} ranges={LOG_TIME_RANGES} />
        <TextField select size="small" label="Limit" value={limit} onChange={(e) => setLimit(Number(e.target.value))} sx={{ minWidth: 100 }}>
          {LIMITS.map((l) => (
            <MenuItem key={l} value={l}>
              {l}
            </MenuItem>
          ))}
        </TextField>
      </FilterBar>
      <FilterBar>
        <TextField size="small" label="Correlation ID" value={correlationId} onChange={(e) => setCorrelationId(e.target.value)} sx={{ minWidth: 180 }} />
        <TextField size="small" label="Trace ID" value={traceId} onChange={(e) => setTraceId(e.target.value)} sx={{ minWidth: 180 }} />
        <TextField size="small" label="Payment Reference" value={paymentReference} onChange={(e) => setPaymentReference(e.target.value)} sx={{ minWidth: 180 }} />
        <SearchBar value={search} onChange={setSearch} placeholder="Search message/logger…" />
      </FilterBar>

      {isLoading && <LoadingState message="Reading log files…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <Stack spacing={1}>
          <Typography variant="caption" color="text.secondary">
            Showing {data.length} entr{data.length === 1 ? 'y' : 'ies'} (bounded to {limit}).
          </Typography>
          <DataTable
            rows={data}
            columns={columns}
            getRowKey={(row) => `${row.service}|${row.timestamp ?? 'na'}|${row.correlationId ?? ''}|${row.message.slice(0, 60)}`}
            emptyTitle="No log lines match"
            emptyMessage="No real log lines matched these filters in the bounded scan window."
          />
        </Stack>
      )}
    </PageContainer>
  )
}
