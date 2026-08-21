/**
 * ENGLISH: The "/metrics" page - real Prometheus/Grafana-style
 * analytics. What it does: a real, selectable time range (5m/15m/30m/
 * 1h/6h/24h - the exact 6 windows PrometheusController's range
 * endpoint allows) drives a real Prometheus range query per chart
 * (CPU, memory, request latency, error rate, Kafka consumer lag,
 * Redis/Lettuce command latency), plus a real Postgres-derived
 * Payments/minute + Success/Failure Rate chart (there is no
 * Prometheus counter for business payment volume - see backend
 * PaymentTimeseriesBucket's javadoc). Below the charts, every one of
 * the backend's other named PromQL queries is still listed in the
 * original accordion for raw inspection. An empty chart when
 * Prometheus/Postgres genuinely returned no samples in the window is
 * the honest, real state - never a fabricated line. Why it exists:
 * required route "/metrics", now fully wired to real Phase 4 range
 * data. How it will communicate with the backend: via
 * usePrometheusRange/usePaymentTimeseries -> prometheusService.ts/
 * postgresService.ts -> GET /api/v1/prometheus/metrics/{slug}/range
 * and GET /api/v1/postgres/payments/timeseries.
 *
 * HINGLISH: "/metrics" page - real Prometheus/Grafana-style analytics.
 * Ye kya karti hai: ek real, selectable time range (5m/15m/30m/1h/6h/
 * 24h - exactly wahi 6 windows jo PrometheusController ka range
 * endpoint allow karta hai) har chart ke liye ek real Prometheus range
 * query drive karta hai (CPU, memory, request latency, error rate,
 * Kafka consumer lag, Redis/Lettuce command latency), plus ek real
 * Postgres-derived Payments/minute + Success/Failure Rate chart
 * (business payment volume ke liye koi Prometheus counter nahi hai -
 * backend PaymentTimeseriesBucket ka javadoc dekho). Charts ke neeche,
 * backend ki har doosri named PromQL query ab bhi original accordion
 * me raw inspection ke liye listed hai. Jab Prometheus/Postgres ne
 * genuinely window me koi samples return na kiye ho toh ek empty chart
 * honest, real state hai - kabhi ek fabricated line nahi. Ye dashboard
 * me kyu hai: required route "/metrics", ab poori tarah real Phase 4
 * range data se wired hai. Backend se kaise connect hogi:
 * usePrometheusRange/usePaymentTimeseries -> prometheusService.ts/
 * postgresService.ts -> GET /api/v1/prometheus/metrics/{slug}/range
 * aur GET /api/v1/postgres/payments/timeseries ke through.
 */
import { useState } from 'react'
import { Accordion, AccordionDetails, AccordionSummary, Chip, Grid2 as Grid, Stack, Typography } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { EmptyState } from '../components/EmptyState'
import { TimeRangeSelector } from '../components/TimeRangeSelector'
import { useAllPrometheusMetrics, usePrometheusRange } from '../hooks/usePrometheusMetrics'
import { usePaymentTimeseries } from '../hooks/usePostgresData'
import { toApiError } from '../api/axiosClient'
import type { TimeRange } from '../types/common'
import type { PrometheusRangeResult } from '../services/prometheusService'

/** The exact 6 windows PrometheusController's range endpoint and PaymentRepository.timeseries() both allow. */
const METRIC_TIME_RANGES: TimeRange[] = [
  { label: '5m', fromMinutesAgo: 5 },
  { label: '15m', fromMinutesAgo: 15 },
  { label: '30m', fromMinutesAgo: 30 },
  { label: '1h', fromMinutesAgo: 60 },
  { label: '6h', fromMinutesAgo: 360 },
  { label: '24h', fromMinutesAgo: 1440 },
]

const LINE_COLORS = ['#6366f1', '#ec4899', '#22c55e', '#f59e0b', '#06b6d4', '#a855f7']

function seriesLabel(labels: Record<string, string>): string {
  const entries = Object.entries(labels)
  if (entries.length === 0) return 'value'
  return entries.map(([k, v]) => `${k}=${v}`).join(', ')
}

/** Merges every real series in a PrometheusRangeResult into one array of {time, <seriesLabel>: value} rows Recharts can plot as multiple lines. */
function toChartRows(result: PrometheusRangeResult | undefined) {
  if (!result || !result.success || result.series.length === 0) return { rows: [], seriesKeys: [] as string[] }
  const seriesKeys = result.series.map((s) => seriesLabel(s.labels))
  const byTimestamp = new Map<number, Record<string, number | string>>()
  result.series.forEach((series, seriesIndex) => {
    series.points.forEach((point) => {
      // Phase 3.10.3 NaN-crash fix: point.value is `number | null` (backend normalizes NaN/+Inf/-Inf,
      // e.g. from a sparse histogram_quantile() window, to `null` rather than a value .toFixed() would
      // crash on). A non-finite point is simply omitted from this timestamp's row - Recharts renders a
      // real gap in the line there, never a fabricated 0.
      if (typeof point.value !== 'number' || !Number.isFinite(point.value)) return
      const row = byTimestamp.get(point.timestampEpochSeconds) ?? { time: new Date(point.timestampEpochSeconds * 1000).toLocaleTimeString() }
      row[seriesKeys[seriesIndex]] = Number(point.value.toFixed(4))
      byTimestamp.set(point.timestampEpochSeconds, row)
    })
  })
  const rows = Array.from(byTimestamp.entries())
    .sort(([a], [b]) => a - b)
    .map(([, row]) => row)
  return { rows, seriesKeys }
}

function RangeChart({ slug, title, rangeMinutes }: { slug: string; title: string; rangeMinutes: number }) {
  const { data, isLoading, isError, error, refetch } = usePrometheusRange(slug, rangeMinutes)
  const { rows, seriesKeys } = toChartRows(data)

  return (
    <Grid size={{ xs: 12, md: 6 }}>
      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>
        {title}
      </Typography>
      {isLoading && <LoadingState message="Querying Prometheus…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && !data.success && <ErrorState message={data.errorMessage ?? 'Query failed.'} onRetry={() => refetch()} />}
      {data?.success && rows.length === 0 && (
        <EmptyState title="No samples" message="Prometheus returned no samples for this query in the selected window." />
      )}
      {rows.length > 0 && (
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} />
            <Tooltip />
            {seriesKeys.length > 1 && <Legend wrapperStyle={{ fontSize: 11 }} />}
            {seriesKeys.map((key, index) => (
              <Line key={key} type="monotone" dataKey={key} stroke={LINE_COLORS[index % LINE_COLORS.length]} dot={false} strokeWidth={2} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      )}
    </Grid>
  )
}

function PaymentsPerMinuteChart({ rangeMinutes }: { rangeMinutes: number }) {
  const { data, isLoading, isError, error, refetch } = usePaymentTimeseries(rangeMinutes)
  const rows = (data ?? []).map((bucket) => ({
    time: new Date(bucket.bucketStart).toLocaleTimeString(),
    total: bucket.total,
    successful: bucket.successful,
    failed: bucket.failed,
  }))

  return (
    <Grid size={{ xs: 12, md: 6 }}>
      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>
        Payments/minute (Success vs. Failure)
      </Typography>
      {isLoading && <LoadingState message="Querying Postgres…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && rows.length === 0 && (
        <EmptyState title="No payment activity" message="No real payment rows fall inside the selected window." />
      )}
      {rows.length > 0 && (
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} />
            <Tooltip />
            <Legend wrapperStyle={{ fontSize: 11 }} />
            <Line type="monotone" dataKey="total" name="total" stroke="#6366f1" dot={false} strokeWidth={2} />
            <Line type="monotone" dataKey="successful" name="successful" stroke="#22c55e" dot={false} strokeWidth={2} />
            <Line type="monotone" dataKey="failed" name="failed" stroke="#ef4444" dot={false} strokeWidth={2} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </Grid>
  )
}

export default function MetricsPage() {
  const [range, setRange] = useState<TimeRange>(METRIC_TIME_RANGES[3])
  const { data, isLoading, isError, error, refetch } = useAllPrometheusMetrics()

  return (
    <PageContainer>
      <PageHeader title="Metrics" description="Real Prometheus + Postgres-derived analytics: throughput, latency, error/success rate, and resource usage." />

      <TimeRangeSelector value={range} onChange={setRange} ranges={METRIC_TIME_RANGES} />

      <Grid container spacing={3}>
        <PaymentsPerMinuteChart rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="request-rate" title="Request Rate (req/s per service)" rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="request-latency-p99" title="Request Latency p99 (s)" rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="error-rate" title="Error Rate (5xx fraction)" rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="cpu-usage" title="CPU Usage" rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="memory-used" title="Memory Used (bytes)" rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="kafka-consumer-lag" title="Kafka Consumer Lag (s)" rangeMinutes={range.fromMinutesAgo} />
        <RangeChart slug="redis-command-latency" title="Redis (Lettuce) Command Latency (s)" rangeMinutes={range.fromMinutesAgo} />
      </Grid>

      <Typography variant="h2" component="h2">
        All Named Metrics
      </Typography>
      {isLoading && <LoadingState message="Querying Prometheus…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data?.map((result) => (
        <Accordion key={result.querySlug} disableGutters>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography fontWeight={600}>{result.querySlug}</Typography>
              <Chip
                size="small"
                label={result.success ? `${result.samples.length} samples` : 'query failed'}
                color={result.success ? 'default' : 'error'}
              />
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 1, fontFamily: 'monospace' }}>
              {result.promQl}
            </Typography>
            {!result.success && (
              <Typography variant="body2" color="error">
                {result.errorMessage}
              </Typography>
            )}
            {result.success && result.samples.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                No samples returned.
              </Typography>
            )}
            {result.success &&
              result.samples.map((sample, index) => (
                <Stack key={index} direction="row" justifyContent="space-between" sx={{ py: 0.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                  <Typography variant="body2" color="text.secondary">
                    {Object.entries(sample.labels).map(([k, v]) => `${k}=${v}`).join(', ') || '(no labels)'}
                  </Typography>
                  <Typography variant="body2" fontWeight={600}>
                    {sample.value}
                  </Typography>
                </Stack>
              ))}
          </AccordionDetails>
        </Accordion>
      ))}
    </PageContainer>
  )
}
