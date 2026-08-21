/**
 * ENGLISH: React Query hook running every named Prometheus query at
 * once (GET /api/v1/prometheus/metrics/all), refreshed every 30s to
 * match Prometheus's own 15s scrape interval reasonably closely.
 *
 * HINGLISH: Har named Prometheus query ko ek saath chalane wala React
 * Query hook (GET /api/v1/prometheus/metrics/all), har 30s me refresh
 * hota hai, Prometheus ke apne 15s scrape interval se reasonably
 * close match karte hue.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchAiPrometheusMetrics, fetchAllPrometheusMetrics, fetchPrometheusRange } from '../services/prometheusService'

export function useAllPrometheusMetrics() {
  return useQuery({ queryKey: ['prometheus-metrics-all'], queryFn: fetchAllPrometheusMetrics, refetchInterval: 30_000 })
}

/** Phase 3.10.3 - AI Metrics page data source, same 30s poll interval as every other Prometheus-backed
 * hook in this app (matches Prometheus's own 15s scrape interval reasonably closely). */
export function useAiPrometheusMetrics() {
  return useQuery({ queryKey: ['prometheus-metrics-ai'], queryFn: fetchAiPrometheusMetrics, refetchInterval: 30_000 })
}

export function usePrometheusRange(slug: string, rangeMinutes: number) {
  return useQuery({
    queryKey: ['prometheus-range', slug, rangeMinutes],
    queryFn: () => fetchPrometheusRange(slug, rangeMinutes),
    refetchInterval: 30_000,
  })
}
