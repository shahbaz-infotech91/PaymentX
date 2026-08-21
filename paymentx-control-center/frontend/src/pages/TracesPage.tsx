/**
 * ENGLISH: The "/traces" page - real Zipkin trace lookup by Trace ID.
 * What it does: lists the real services Zipkin knows about, lets the
 * user enter a real Trace ID (copied from a Zipkin UI, a log line, or
 * a response header) and fetches its real spans/duration/status - a
 * trace with zero spans is a real, honest error, never an invented
 * empty trace. Why it exists: required route "/traces", now wired to
 * the real Phase 2 backend. How it will communicate with the backend:
 * via useZipkinServices/useZipkinTrace -> zipkinService.ts -> GET
 * /api/v1/zipkin/*.
 *
 * HINGLISH: "/traces" page - Trace ID se real Zipkin trace lookup. Ye
 * kya karti hai: Zipkin ko jo real services pata hain unhe list karta
 * hai, user ko ek real Trace ID enter karne deta hai (Zipkin UI, ek
 * log line, ya ek response header se copy kiya gaya) aur uske real
 * spans/duration/status fetch karta hai - zero spans wali trace ek
 * real, honest error hai, kabhi ek invented empty trace nahi. Ye
 * dashboard me kyu hai: required route "/traces", ab real Phase 2
 * backend se wired hai. Backend se kaise connect hogi:
 * useZipkinServices/useZipkinTrace -> zipkinService.ts -> GET
 * /api/v1/zipkin/* ke through.
 */
import { useState } from 'react'
import { Box, Button, Chip, Stack, Tooltip, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { SearchBar } from '../components/SearchBar'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { EmptyState } from '../components/EmptyState'
import { useZipkinServices, useZipkinTrace } from '../hooks/useZipkinTraces'
import { toApiError } from '../api/axiosClient'
import type { ZipkinSpan, ZipkinTraceDetail } from '../services/zipkinService'

const TIMELINE_COLORS = ['#6366f1', '#ec4899', '#22c55e', '#f59e0b', '#06b6d4', '#a855f7']

/** A real, proportional waterfall over each real span's real start offset (from the trace's earliest real timestamp) and real duration - never a synthetic layout. */
function SpanTimeline({ trace }: { trace: ZipkinTraceDetail }) {
  const earliest = trace.earliestTimestampEpochMicros ?? Math.min(...trace.spans.map((s) => s.timestampEpochMicros ?? 0))
  const total = trace.totalDurationMicros > 0 ? trace.totalDurationMicros : 1
  const serviceColor = new Map<string, string>()
  let nextColor = 0

  return (
    <Stack spacing={0.75} sx={{ my: 2 }}>
      {trace.spans
        .slice()
        .sort((a, b) => (a.timestampEpochMicros ?? 0) - (b.timestampEpochMicros ?? 0))
        .map((span) => {
          const start = span.timestampEpochMicros ?? earliest
          const offsetPercent = Math.max(0, Math.min(100, ((start - earliest) / total) * 100))
          const widthPercent = Math.max(0.5, Math.min(100 - offsetPercent, ((span.durationMicros ?? 0) / total) * 100))
          const svc = span.serviceName ?? 'unknown'
          if (!serviceColor.has(svc)) {
            serviceColor.set(svc, TIMELINE_COLORS[nextColor % TIMELINE_COLORS.length])
            nextColor += 1
          }
          return (
            <Stack key={span.spanId} direction="row" spacing={1} alignItems="center">
              <Typography variant="caption" sx={{ width: 220, flexShrink: 0 }} noWrap title={`${svc} · ${span.name ?? ''}`}>
                {svc} · {span.name ?? span.spanId}
              </Typography>
              <Box sx={{ position: 'relative', flexGrow: 1, height: 14, bgcolor: 'action.hover', borderRadius: 0.5 }}>
                <Tooltip title={`${((span.durationMicros ?? 0) / 1000).toFixed(2)} ms`}>
                  <Box
                    sx={{
                      position: 'absolute',
                      left: `${offsetPercent}%`,
                      width: `${widthPercent}%`,
                      height: '100%',
                      bgcolor: serviceColor.get(svc),
                      borderRadius: 0.5,
                    }}
                  />
                </Tooltip>
              </Box>
            </Stack>
          )
        })}
    </Stack>
  )
}

const spanColumns: DataTableColumn<ZipkinSpan>[] = [
  { key: 'service', label: 'Service', render: (row) => row.serviceName ?? '—' },
  { key: 'name', label: 'Span', render: (row) => row.name ?? '—' },
  { key: 'kind', label: 'Kind', render: (row) => row.kind ?? '—' },
  { key: 'duration', label: 'Duration (µs)', align: 'right', render: (row) => row.durationMicros ?? '—' },
  { key: 'spanId', label: 'Span ID', render: (row) => row.spanId },
  { key: 'parentId', label: 'Parent ID', render: (row) => row.parentId ?? '(root)' },
]

export default function TracesPage() {
  const [traceIdInput, setTraceIdInput] = useState('')
  const services = useZipkinServices()
  const trace = useZipkinTrace(traceIdInput.trim())

  return (
    <PageContainer>
      <PageHeader title="Traces" description="Real distributed trace lookup via Zipkin." />

      {services.data && services.data.length > 0 && (
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {services.data.map((service) => (
            <Chip key={service} size="small" label={service} />
          ))}
        </Stack>
      )}

      <Stack direction="row" spacing={1} alignItems="center">
        <SearchBar value={traceIdInput} onChange={setTraceIdInput} placeholder="Enter a real Trace ID…" />
        <Button variant="contained" disabled={!traceIdInput.trim()} onClick={() => trace.refetch()}>
          Look up
        </Button>
      </Stack>

      {trace.isFetching && <LoadingState message="Looking up trace…" />}
      {trace.isError && <ErrorState message={toApiError(trace.error).message} />}
      {trace.data && (
        <>
          <Stack direction="row" spacing={2} alignItems="center">
            <Chip color={trace.data.status === 'OK' ? 'success' : 'error'} label={trace.data.status} />
            <Typography variant="body2" color="text.secondary">
              {trace.data.spans.length} spans across {trace.data.services.length} service(s), total duration{' '}
              {(trace.data.totalDurationMicros / 1000).toFixed(1)} ms
            </Typography>
          </Stack>
          <Typography variant="h2" component="h2">
            Timeline
          </Typography>
          <SpanTimeline trace={trace.data} />
          <Typography variant="h2" component="h2">
            Spans
          </Typography>
          <DataTable
            rows={trace.data.spans}
            columns={spanColumns}
            getRowKey={(row) => row.spanId}
            emptyTitle="No spans"
            emptyMessage="This trace has no spans."
          />
        </>
      )}
      {!trace.data && !trace.isFetching && !trace.isError && (
        <EmptyState title="No trace looked up yet" message="Enter a real Trace ID above and click Look up." />
      )}
    </PageContainer>
  )
}
