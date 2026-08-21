/**
 * ENGLISH: The real API client for the Phase 2 Prometheus domain.
 * What it does: lists the backend's fixed, named PromQL-query catalog
 * and runs real queries against it - there is no function here that
 * sends free-form PromQL, matching the backend's "no raw PromQL from
 * the browser" SSRF/DoS defense. Every interface mirrors a real
 * backend record (see backend dto/prometheus/*.java). Why it exists:
 * the frontend must never call Prometheus directly. How it will
 * communicate with the backend: real HTTP calls to
 * PrometheusController.
 *
 * HINGLISH: Phase 2 ke Prometheus domain ke liye real API client. Ye
 * kya karti hai: backend ka fixed, named PromQL-query catalog list
 * karta hai aur uske against real queries chalata hai - yahan koi
 * function nahi hai jo free-form PromQL bheje, backend ki "browser se
 * raw PromQL nahi" SSRF/DoS defense se match karte hue. Har interface
 * ek real backend record ko mirror karti hai (backend
 * dto/prometheus/*.java dekho). Ye dashboard me kyu hai: frontend ko
 * kabhi Prometheus ko directly call nahi karna chahiye. Backend se
 * kaise connect hogi: PrometheusController ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface PrometheusMetricQueryDescriptor {
  slug: string
  description: string
  promQl: string
}

/**
 * Phase 3.10.3 NaN-crash fix: `value` is `number | null`, mirroring backend PrometheusSample.java's
 * `Double` exactly. `null` means Prometheus could not compute a meaningful number for this series
 * (NaN/+Inf/-Inf - most commonly a sparse histogram_quantile()) - never treat it as, or convert it to,
 * 0. Every consumer must check for `null` before doing arithmetic or calling `.toFixed()`.
 */
export interface PrometheusSample {
  labels: Record<string, string>
  value: number | null
  timestampEpochSeconds: number
}

export interface PrometheusQueryResult {
  querySlug: string
  promQl: string
  success: boolean
  errorMessage: string | null
  samples: PrometheusSample[]
}

export async function fetchPrometheusCatalog(): Promise<PrometheusMetricQueryDescriptor[]> {
  const response = await axiosClient.get<ApiResponse<PrometheusMetricQueryDescriptor[]>>('/api/v1/prometheus/metrics')
  return response.data.data ?? []
}

export async function fetchAllPrometheusMetrics(): Promise<PrometheusQueryResult[]> {
  const response = await axiosClient.get<ApiResponse<PrometheusQueryResult[]>>('/api/v1/prometheus/metrics/all')
  return response.data.data ?? []
}

/**
 * Phase 3.10.3 - runs only the AI-Platform-scoped subset of the backend's fixed named-query catalog
 * (the "ai-" slug prefix - see backend PrometheusMetricQuery's javadoc), for the AI Metrics page.
 * Deliberately a separate call from fetchAllPrometheusMetrics() - reusing that one would run the
 * entire ~37-entry catalog (infra/business queries the AI page has no use for) on every load.
 */
export async function fetchAiPrometheusMetrics(): Promise<PrometheusQueryResult[]> {
  const response = await axiosClient.get<ApiResponse<PrometheusQueryResult[]>>('/api/v1/prometheus/metrics/ai')
  return response.data.data ?? []
}

/** Phase 3.10.3 NaN-crash fix: `value` is `number | null` - see PrometheusSample's own comment above,
 * this record shares the identical rationale (mirrors backend PrometheusRangePoint.java's `Double`). */
export interface PrometheusRangePoint {
  timestampEpochSeconds: number
  value: number | null
}

export interface PrometheusRangeSeries {
  labels: Record<string, string>
  points: PrometheusRangePoint[]
}

export interface PrometheusRangeResult {
  querySlug: string
  promQl: string
  success: boolean
  errorMessage: string | null
  series: PrometheusRangeSeries[]
}

/** rangeMinutes must be one of the 6 real windows PrometheusController's range endpoint allows (5/15/30/60/360/1440) - an unrecognized value quietly falls back to 60 server-side. */
export async function fetchPrometheusRange(slug: string, rangeMinutes: number): Promise<PrometheusRangeResult> {
  const response = await axiosClient.get<ApiResponse<PrometheusRangeResult>>(`/api/v1/prometheus/metrics/${encodeURIComponent(slug)}/range`, {
    params: { rangeMinutes },
  })
  if (!response.data.data) throw new Error('Backend returned an empty Prometheus range payload.')
  return response.data.data
}
