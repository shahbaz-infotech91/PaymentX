/**
 * ENGLISH: A toggle-button group for picking a relative time window
 * (15m/1h/6h/24h). What it does: controlled selection over the
 * TimeRange type (src/types/common.ts) - it holds no data itself. Why
 * it exists: one of the 14 required reusable components - Metrics,
 * Traces, and Logs pages will all need the exact same "how far back"
 * control once they query real Prometheus/Zipkin/log data in Phase 2.
 * How it will communicate with the backend: N/A on its own - a future
 * caller turns the selected TimeRange into a real query parameter
 * (e.g. Prometheus's `start`/`end`).
 *
 * HINGLISH: Ek relative time window (15m/1h/6h/24h) chunne ke liye
 * toggle-button group. Ye kya karti hai: TimeRange type (src/types/
 * common.ts) par controlled selection hai - iske paas khud koi data
 * nahi hai. Ye dashboard me kyu hai: 14 required reusable components
 * me se ek hai - Metrics, Traces, aur Logs pages sabko exactly wahi
 * "kitna peeche" control chahiye hoga jab wo Phase 2 me real
 * Prometheus/Zipkin/log data query karenge. Backend se kaise connect
 * hogi: khud se N/A - koi future caller selected TimeRange ko ek real
 * query parameter me badlega (jaise Prometheus ka `start`/`end`).
 */
import { ToggleButton, ToggleButtonGroup } from '@mui/material'
import type { TimeRange } from '../types/common'

export const DEFAULT_TIME_RANGES: TimeRange[] = [
  { label: '15m', fromMinutesAgo: 15 },
  { label: '1h', fromMinutesAgo: 60 },
  { label: '6h', fromMinutesAgo: 360 },
  { label: '24h', fromMinutesAgo: 1440 },
]

interface TimeRangeSelectorProps {
  value: TimeRange
  onChange: (range: TimeRange) => void
  ranges?: TimeRange[]
}

export function TimeRangeSelector({ value, onChange, ranges = DEFAULT_TIME_RANGES }: TimeRangeSelectorProps) {
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={value.label}
      onChange={(_event, newLabel: string | null) => {
        if (!newLabel) return
        const next = ranges.find((range) => range.label === newLabel)
        if (next) onChange(next)
      }}
    >
      {ranges.map((range) => (
        <ToggleButton key={range.label} value={range.label}>
          {range.label}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  )
}
