/**
 * ENGLISH: Phase 3.10.3 - the "/ai-metrics" page, product-native operational visibility over the
 * already-existing ~60 real Micrometer AI Platform metrics (Prompt/LLM/Embedding/Vector/RAG/MCP/Agent -
 * see PAYMENTX_PHASE_3_10_3_AI_METRICS_UI_DESIGN.md's full inventory). What it does: one real backend
 * call (GET /api/v1/prometheus/metrics/ai, via useAiPrometheusMetrics()) drives every card below - the
 * same allowlisted-enum, no-raw-PromQL PrometheusClient/PrometheusMetricsService/PrometheusController
 * architecture the pre-existing /metrics page already uses in production, just scoped to the AI subset
 * (see PrometheusMetricsService.runAi()'s javadoc for why a separate, smaller call rather than reusing
 * the full /metrics/all catalog). "Overall Health" reuses useAiHealth() as-is (AiPlatformHealth.tsx) -
 * no second health implementation. Cards over charts, per this phase's own Step 6 preference and this
 * design's Step 10 performance note (a chart-per-metric page would mean N range queries on top of the
 * one instant-query call this page actually needs). Every card is independently loading/empty/error-safe
 * - one failed or missing metric never blocks any other card or the page itself (Step 8).
 *
 * HINGLISH: Phase 3.10.3 - "/ai-metrics" page, already-existing ~60 real Micrometer AI Platform metrics
 * (Prompt/LLM/Embedding/Vector/RAG/MCP/Agent) par product-native operational visibility. Ye kya karti
 * hai: ek real backend call (GET /api/v1/prometheus/metrics/ai, useAiPrometheusMetrics() ke through)
 * neeche ke har card ko drive karta hai - wahi allowlisted-enum, no-raw-PromQL
 * PrometheusClient/PrometheusMetricsService/PrometheusController architecture jo pre-existing /metrics
 * page already production me use karta hai, bas AI subset tak scoped. "Overall Health" useAiHealth() ko
 * as-is reuse karta hai (AiPlatformHealth.tsx) - koi doosra health implementation nahi. Har card
 * independently loading/empty/error-safe hai - ek failed ya missing metric kabhi kisi doosre card ya
 * poore page ko block nahi karta.
 */
import { Card, CardContent, Grid2 as Grid, Stack, Tooltip, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { AiPlatformHealth } from '../components/ai/AiPlatformHealth'
import { useAiPrometheusMetrics } from '../hooks/usePrometheusMetrics'
import { toApiError } from '../api/axiosClient'
import type { PrometheusQueryResult, PrometheusSample } from '../services/prometheusService'

/**
 * Phase 3.10.3 NaN-crash fix: `PrometheusSample.value` is `number | null` (backend now normalizes
 * NaN/+Inf/-Inf - most commonly from a sparse histogram_quantile() - to `null` rather than the
 * Jackson-default quoted JSON string "NaN"/"Infinity" that used to crash this page's `.toFixed()`
 * calls). `Number.isFinite` is the correct guard here, not a `!== null` check alone: it also rejects a
 * value that somehow arrived as `NaN`/`Infinity` directly (defense-in-depth per this task's own "the
 * frontend must remain defensive even if backend normalization is implemented" requirement), and it
 * rejects `null`/`undefined` too, without ever needing `Number(value || 0)` (which would incorrectly
 * turn a real missing value into a fabricated 0).
 */
function isUsableValue(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

/** Sums only the finite samples. Returns `null` (never 0) when there are zero usable samples - Step 8's
 * "never substitute zero for an unknown metric" applies exactly as much to "every sample was NaN" as it
 * does to "there were no samples at all". */
function sumSamples(samples: PrometheusSample[]): number | null {
  const usable = samples.map((s) => s.value).filter(isUsableValue)
  if (usable.length === 0) return null
  return usable.reduce((total, value) => total + value, 0)
}

function formatRate(value: number): string {
  if (!isUsableValue(value)) return 'N/A'
  return `${value.toFixed(value < 1 ? 3 : 2)}/s`
}

function formatLatencySeconds(value: number): string {
  if (!isUsableValue(value)) return 'N/A'
  if (value < 1) return `${Math.round(value * 1000)} ms`
  return `${value.toFixed(2)} s`
}

function sampleLabel(sample: PrometheusSample): string {
  const entries = Object.entries(sample.labels)
  if (entries.length === 0) return '(unlabeled)'
  return entries.map(([k, v]) => `${k}=${v}`).join(', ')
}

interface AiMetricCardProps {
  results: PrometheusQueryResult[] | undefined
  slug: string
  label: string
  format: (value: number) => string
  /** When there is more than one labelled series (e.g. per-provider, per-tool), show each one below the
   * aggregate headline value instead of silently collapsing the detail away. */
  showBreakdown?: boolean
}

/** One operational metric card - honestly represents four distinct real states: the containing query
 * not having run yet (result missing entirely, defensive), the query having genuinely failed against
 * Prometheus, the query having succeeded with zero real samples (a real "no data" state - Step 8's
 * "never substitute zero for an unknown metric"), and a real value. */
function AiMetricCard({ results, slug, label, format, showBreakdown }: AiMetricCardProps) {
  const result = results?.find((r) => r.querySlug === slug)

  let content: ReactNode
  let helperText: string | undefined

  if (!result) {
    content = (
      <Typography variant="body2" color="text.secondary">
        Unavailable
      </Typography>
    )
    helperText = 'This metric was not returned by the backend.'
  } else if (!result.success) {
    content = (
      <Tooltip title={result.errorMessage ?? 'Query failed.'}>
        <Typography variant="body2" color="error">
          Unavailable
        </Typography>
      </Tooltip>
    )
    helperText = 'Prometheus query failed - hover for detail.'
  } else if (result.samples.length === 0) {
    content = (
      <Typography variant="body2" color="text.secondary">
        No data
      </Typography>
    )
    helperText = 'No samples in this window.'
  } else {
    // Phase 3.10.3 NaN-crash fix: `total` is `number | null` - a sparse histogram_quantile() (or any
    // series whose only samples are non-finite) must render the same honest "No data" state as zero
    // samples, never a fabricated 0 and never a crash.
    const total = sumSamples(result.samples)
    if (total === null) {
      content = (
        <Typography variant="body2" color="text.secondary">
          No data
        </Typography>
      )
      helperText = 'Prometheus could not compute a meaningful value for this window yet.'
    } else {
      content = (
        <Typography variant="h5" component="div" fontWeight={700}>
          {format(total)}
        </Typography>
      )
      // format() itself is defensive (isUsableValue-guarded) - a non-finite individual sample renders
      // as "N/A" in the breakdown rather than crashing, so no separate filtering/narrowing is needed here.
      if (showBreakdown && result.samples.length > 1) {
        helperText = result.samples
          .map((s) => `${sampleLabel(s)}: ${format(s.value ?? NaN)}`)
          .join(' · ')
      }
    }
  }

  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
      <Card variant="outlined">
        <CardContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
            {label}
          </Typography>
          {content}
          {helperText && (
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
              {helperText}
            </Typography>
          )}
        </CardContent>
      </Card>
    </Grid>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Stack spacing={1.5}>
      <Typography variant="h2" component="h2">
        {title}
      </Typography>
      <Grid container spacing={2}>
        {children}
      </Grid>
    </Stack>
  )
}

export default function AiMetricsPage() {
  const { data, isLoading, isError, error, refetch } = useAiPrometheusMetrics()

  return (
    <PageContainer>
      <PageHeader
        title="AI Metrics"
        description="Operational visibility over the already-existing AI Platform metrics (Prompt, LLM, Embedding, Vector, RAG, MCP, Agent), scraped by the same Prometheus every other PaymentX metric already uses."
      />

      <Stack spacing={1.5}>
        <Typography variant="h2" component="h2">
          Overall Health
        </Typography>
        <AiPlatformHealth />
      </Stack>

      {isLoading && <LoadingState message="Querying Prometheus for AI Platform metrics…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}

      {!isLoading && !isError && (
        <>
          <Section title="LLM">
            <AiMetricCard results={data} slug="ai-llm-request-rate" label="Requests" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-llm-success-rate" label="Success" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-llm-failure-rate" label="Errors" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-llm-latency-p95" label="Latency (p95)" format={formatLatencySeconds} />
          </Section>

          <Section title="RAG">
            <AiMetricCard results={data} slug="ai-rag-query-rate" label="Queries" format={formatRate} />
            <AiMetricCard results={data} slug="ai-rag-success-rate" label="Retrieval Success" format={formatRate} />
            <AiMetricCard results={data} slug="ai-rag-insufficient-context-rate" label="No Relevant Context" format={formatRate} />
            <AiMetricCard results={data} slug="ai-rag-failure-rate" label="Errors" format={formatRate} />
            <AiMetricCard results={data} slug="ai-rag-latency-p95" label="Latency (p95)" format={formatLatencySeconds} />
          </Section>

          <Section title="MCP">
            <AiMetricCard results={data} slug="ai-mcp-tool-call-rate" label="Tool Calls" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-mcp-tool-success-rate" label="Success" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-mcp-tool-failure-rate" label="Errors" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-mcp-tool-denied-rate" label="Denied" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-mcp-tool-latency-p95" label="Latency (p95)" format={formatLatencySeconds} />
          </Section>

          <Section title="Agent">
            <AiMetricCard results={data} slug="ai-agent-execution-rate" label="Executions" format={formatRate} />
            <AiMetricCard results={data} slug="ai-agent-success-rate" label="Success" format={formatRate} />
            <AiMetricCard results={data} slug="ai-agent-failure-rate" label="Errors" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-agent-latency-p95" label="Latency (p95)" format={formatLatencySeconds} />
          </Section>

          <Section title="Embedding">
            <AiMetricCard results={data} slug="ai-embedding-request-rate" label="Requests" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-embedding-latency-p95" label="Latency (p95)" format={formatLatencySeconds} />
          </Section>

          <Section title="Vector">
            <AiMetricCard results={data} slug="ai-vector-search-rate" label="Searches" format={formatRate} />
            <AiMetricCard results={data} slug="ai-vector-latency-p95" label="Latency (p95)" format={formatLatencySeconds} />
          </Section>

          <Section title="Prompt">
            <AiMetricCard results={data} slug="ai-prompt-request-rate" label="Requests" format={formatRate} showBreakdown />
            <AiMetricCard results={data} slug="ai-prompt-failure-rate" label="Errors" format={formatRate} />
          </Section>
        </>
      )}
    </PageContainer>
  )
}
