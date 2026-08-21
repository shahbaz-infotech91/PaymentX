/**
 * ENGLISH: The "/" Home page - PaymentX's real operations dashboard
 * (Phase 3). What it does: two real data sources combined, never
 * fabricated. (1) Payment lifecycle stats - total/successful/failed/
 * pending/processing counts, success/failure rate, TPS, throughput,
 * and average latency - all computed by one real GROUP BY query
 * against paymentx_payment.payment (GET /api/v1/postgres/payments/
 * stats), so this tile works even when Prometheus has no current
 * scrape data. (2) 9 real service health cards - live Actuator status
 * and response time (GET /api/v1/services), enriched where available
 * with real per-service CPU/memory read from Prometheus's
 * cpu-usage/memory-used named queries and matched to each service by
 * its real job name (paymentx-<slug>, from infra/prometheus.yml).
 * Build-info (/actuator/info) is genuinely empty on every PaymentX
 * service (no spring-boot-maven-plugin build-info goal configured
 * anywhere - verified) so Version is shown as "Unavailable" rather
 * than an extra network call for a field that would always be blank.
 * Why it exists: this IS the Phase 3 Home requirement.
 *
 * HINGLISH: "/" Home page - PaymentX ka real operations dashboard
 * (Phase 3). Ye kya karti hai: do real data sources combine kiye gaye,
 * kabhi fabricate nahi. (1) Payment lifecycle stats - total/
 * successful/failed/pending/processing counts, success/failure rate,
 * TPS, throughput, aur average latency - sab ek real GROUP BY query se
 * compute kiye gaye paymentx_payment.payment ke against (GET
 * /api/v1/postgres/payments/stats), isliye ye tile tab bhi kaam karta
 * hai jab Prometheus ke paas koi current scrape data na ho. (2) 9 real
 * service health cards - live Actuator status aur response time (GET
 * /api/v1/services), jahan available ho wahan real per-service CPU/
 * memory se enrich kiya gaya jo Prometheus ke cpu-usage/memory-used
 * named queries se padha gaya aur har service ke real job name
 * (paymentx-<slug>, infra/prometheus.yml se) se match kiya gaya.
 * Build-info (/actuator/info) har PaymentX service par genuinely empty
 * hai (kahin bhi spring-boot-maven-plugin build-info goal configure
 * nahi hai - verified) isliye Version "Unavailable" dikhaya jaata hai,
 * ek aise field ke liye extra network call ke bajaye jo hamesha blank
 * rahega. Ye dashboard me kyu hai: yehi Phase 3 Home requirement HAI.
 */
import { Card, CardContent, Chip, Grid2 as Grid, Stack, Typography } from '@mui/material'
import { motion } from 'framer-motion'
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined'
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline'
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty'
import AutorenewIcon from '@mui/icons-material/Autorenew'
import SpeedOutlinedIcon from '@mui/icons-material/SpeedOutlined'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { MetricCard } from '../components/MetricCard'
import { StatusBadge } from '../components/StatusBadge'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { useServicesHealth } from '../hooks/useServicesHealth'
import { usePaymentStats } from '../hooks/usePostgresData'
import { useAllPrometheusMetrics } from '../hooks/usePrometheusMetrics'
import { toApiError } from '../api/axiosClient'
import type { PrometheusQueryResult } from '../services/prometheusService'
import type { Status } from '../types/common'

function toStatus(raw: string | null): Status {
  if (raw === 'UP') return 'UP'
  if (raw === 'DOWN' || raw === 'UNREACHABLE') return 'DOWN'
  if (raw === null) return 'UNKNOWN'
  return 'DEGRADED'
}

/** Real job-name convention from infra/prometheus.yml's scrape_configs - never guessed. */
function jobNameFor(serviceSlug: string): string {
  return `paymentx-${serviceSlug}`
}

/** Phase 3.10.3 NaN-crash fix: PrometheusSample.value is `number | null` (backend normalizes
 * NaN/+Inf/-Inf to `null` rather than a value this arithmetic could silently corrupt) - only finite
 * samples are summed; `null` here (as already documented by this function's own return type) means
 * Prometheus had nothing meaningful, never a fabricated 0. */
function findSampleValue(result: PrometheusQueryResult | undefined, job: string): number | null {
  if (!result || !result.success) return null
  const matches = result.samples.filter((s) => s.labels.job === job && typeof s.value === 'number' && Number.isFinite(s.value))
  if (matches.length === 0) return null
  return matches.reduce((sum, s) => sum + (s.value as number), 0)
}

function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`
}

function formatLatency(ms: number | null): string {
  if (ms === null) return '—'
  return ms < 1000 ? `${ms.toFixed(0)} ms` : `${(ms / 1000).toFixed(2)} s`
}

function formatMemory(bytes: number | null): string {
  if (bytes === null) return 'Unavailable'
  const mb = bytes / (1024 * 1024)
  return `${mb.toFixed(0)} MB`
}

export default function DashboardPage() {
  const services = useServicesHealth()
  const stats = usePaymentStats()
  const metrics = useAllPrometheusMetrics()

  const cpuResult = metrics.data?.find((r) => r.querySlug === 'cpu-usage')
  const memoryResult = metrics.data?.find((r) => r.querySlug === 'memory-used')
  const errorRateResult = metrics.data?.find((r) => r.querySlug === 'error-rate')

  // Phase 3.10.3 NaN-crash fix: was `!Number.isNaN(s.value)`, which never actually filtered anything -
  // a non-finite Prometheus value used to arrive as the JSON string "NaN", and Number.isNaN("NaN") is
  // false (no coercion). Now that the backend normalizes NaN/+Inf/-Inf to a real JSON `null`, a plain
  // finite-number check is both correct and sufficient.
  const errorRateSamples = errorRateResult?.success
    ? errorRateResult.samples.filter((s): s is typeof s & { value: number } => typeof s.value === 'number' && Number.isFinite(s.value))
    : []
  const avgErrorRate = errorRateSamples.length > 0
    ? (errorRateSamples.reduce((sum, s) => sum + s.value, 0) / errorRateSamples.length) * 100
    : null

  return (
    <PageContainer>
      <PageHeader title="Home" description="PaymentX operations at a glance - real payment lifecycle and real service health." />

      <Typography variant="h2" component="h2">
        Payment Lifecycle
      </Typography>
      {stats.isLoading && <LoadingState message="Loading payment statistics…" />}
      {stats.isError && <ErrorState message={toApiError(stats.error).message} onRetry={() => stats.refetch()} />}
      {stats.data && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Total Payments" value={stats.data.totalPayments.toLocaleString()} icon={<DnsOutlinedIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Successful" value={stats.data.successful.toLocaleString()} icon={<CheckCircleOutlineIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Failed" value={stats.data.failed.toLocaleString()} icon={<ErrorOutlineIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Pending" value={stats.data.pending.toLocaleString()} icon={<HourglassEmptyIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Processing" value={stats.data.processing.toLocaleString()} icon={<AutorenewIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Success Rate" value={formatPercent(stats.data.successRatePercent)} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Failure Rate" value={formatPercent(stats.data.failureRatePercent)} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="TPS" value={stats.data.tps.toFixed(3)} icon={<SpeedOutlinedIcon />} helperText="transactions/sec, trailing 1h" />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Throughput" value={stats.data.paymentsLastHour.toLocaleString()} helperText="payments in the last hour" icon={<TrendingUpOutlinedIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard label="Average Latency" value={formatLatency(stats.data.averageLatencyMillis)} helperText="created→settled, real payments" />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <MetricCard
              label="Error Rate"
              value={avgErrorRate !== null ? formatPercent(avgErrorRate) : 'Unavailable'}
              helperText={avgErrorRate !== null ? 'live from Prometheus' : 'no current Prometheus samples'}
            />
          </Grid>
        </Grid>
      )}

      <Typography variant="h2" component="h2">
        Service Health
      </Typography>
      {services.isLoading && <LoadingState message="Checking service health…" />}
      {services.isError && <ErrorState message={toApiError(services.error).message} onRetry={() => services.refetch()} />}
      {services.data && (
        <Grid container spacing={2}>
          {services.data.map((service, index) => {
            const job = jobNameFor(service.serviceSlug)
            const cpu = findSampleValue(cpuResult, job)
            const memory = findSampleValue(memoryResult, job)
            return (
              <Grid key={service.serviceSlug} size={{ xs: 12, sm: 6, md: 4 }}>
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.25, delay: index * 0.03 }}
                >
                  <Card variant="outlined">
                    <CardContent>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                        <Typography variant="subtitle1" fontWeight={600}>
                          {service.serviceName}
                        </Typography>
                        <StatusBadge status={toStatus(service.status)} />
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          Response time: {service.responseTimeMillis} ms
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Version: Unavailable <Chip size="small" label="no build-info" variant="outlined" sx={{ ml: 0.5, height: 16, fontSize: 10 }} />
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          CPU: {cpu !== null ? formatPercent(cpu * 100) : 'Unavailable'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Memory: {formatMemory(memory)}
                        </Typography>
                        {service.errorMessage && (
                          <Typography variant="caption" color="error">
                            {service.errorMessage}
                          </Typography>
                        )}
                      </Stack>
                    </CardContent>
                  </Card>
                </motion.div>
              </Grid>
            )
          })}
        </Grid>
      )}
    </PageContainer>
  )
}
