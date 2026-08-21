/**
 * ENGLISH: Small, pure formatting helpers reused across components
 * (MetricCard, DataTable, HealthIndicator). What it does: formats
 * ISO timestamps into a readable local time and formats a Status value
 * into display-friendly text. Why it exists: keeps date/number
 * formatting logic out of individual components so every page renders
 * timestamps identically. How it will communicate with the backend:
 * N/A - pure client-side formatting of data already fetched elsewhere.
 *
 * HINGLISH: Chhote, pure formatting helpers jo components (MetricCard,
 * DataTable, HealthIndicator) me reuse hote hain. Ye kya karti hai:
 * ISO timestamps ko readable local time me format karta hai aur ek
 * Status value ko display-friendly text me format karta hai. Ye
 * dashboard me kyu hai: date/number formatting logic ko individual
 * components se bahar rakhta hai taaki har page timestamps ko
 * identically render kare. Backend se kaise connect hogi: N/A - pure
 * client-side formatting hai us data ki jo kahin aur se already
 * fetch ho chuka hai.
 */
import type { Status } from '../types/common'

export function formatTimestamp(iso: string | undefined | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  })
}

export function formatStatusLabel(status: Status): string {
  switch (status) {
    case 'UP':
      return 'Healthy'
    case 'DOWN':
      return 'Down'
    case 'DEGRADED':
      return 'Degraded'
    default:
      return 'Unknown'
  }
}

/** Real byte counts (Postgres/Kafka/Redis/RabbitMQ payload sizes) formatted as human-readable units - never invented, just re-scaled. */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return '—'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const exponent = Math.min(Math.floor(Math.log(Math.abs(bytes)) / Math.log(1024)), units.length - 1)
  const value = bytes / Math.pow(1024, exponent)
  return `${value.toFixed(exponent === 0 ? 0 : 2)} ${units[exponent]}`
}
