/**
 * ENGLISH: The "/e2e" page - a real, bounded "Run Complete Payment
 * Flow" trigger. What it does: on confirm, starts one real E2E run
 * (backend: E2EFlowService) that submits a real payment through the
 * real API Gateway using a real, ephemeral test API key, then polls
 * the real payment/audit/notification state as the real platform
 * processes it - every stage chip below reflects a real signal this
 * backend actually observed, never a simulated animation. The run is
 * bounded (the backend enforces a finite maxDurationSeconds and
 * reports TIMEOUT, never SUCCESS, if that deadline is hit) - this page
 * polls only while overallStatus is RUNNING and stops the moment a
 * terminal state arrives. Why it exists: required Phase 5 "E2E Payment
 * Flow" module. How it will communicate with the backend: via
 * useStartE2ERun/useE2ERun/useE2EHistory -> e2eService.ts -> POST/GET
 * /api/v1/e2e/*.
 *
 * HINGLISH: "/e2e" page - ek real, bounded "Run Complete Payment Flow"
 * trigger. Ye kya karti hai: confirm par, ek real E2E run start karta
 * hai (backend: E2EFlowService) jo real API Gateway ke through, ek
 * real, ephemeral test API key use karke, ek real payment submit karta
 * hai, phir real platform jaise-jaise ise process karta hai real
 * payment/audit/notification state poll karta hai - neeche har stage
 * chip ek real signal reflect karta hai jo is backend ne actually
 * observe kiya, kabhi ek simulated animation nahi. Run bounded hai
 * (backend ek finite maxDurationSeconds enforce karta hai aur agar wo
 * deadline hit ho jaye toh TIMEOUT report karta hai, kabhi SUCCESS
 * nahi) - ye page sirf tab tak poll karta hai jab tak overallStatus
 * RUNNING hai aur jaise hi ek terminal state aaye rukta hai. Ye
 * dashboard me kyu hai: required Phase 5 "E2E Payment Flow" module.
 * Backend se kaise connect hogi: useStartE2ERun/useE2ERun/useE2EHistory
 * -> e2eService.ts -> POST/GET /api/v1/e2e/* ke through.
 */
import { useEffect, useState } from 'react'
import { Alert, Box, Button, Chip, LinearProgress, Stack, Typography } from '@mui/material'
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { ConfirmationDialog } from '../components/ConfirmationDialog'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { ErrorState } from '../components/ErrorState'
import { EmptyState } from '../components/EmptyState'
import { useStartE2ERun, useE2ERun, useE2EHistory } from '../hooks/useE2EFlow'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import type { E2ERunResult, E2EStageStatus } from '../services/e2eService'

const STAGE_COLOR: Record<E2EStageStatus, 'default' | 'info' | 'success' | 'error' | 'warning'> = {
  PENDING: 'default',
  RUNNING: 'info',
  SUCCESS: 'success',
  FAILED: 'error',
  TIMEOUT: 'warning',
}

const OVERALL_LABEL: Record<E2EStageStatus, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running',
  SUCCESS: 'Success',
  FAILED: 'Failed',
  TIMEOUT: 'Timed Out',
}

function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

const historyColumns: DataTableColumn<E2ERunResult>[] = [
  { key: 'overallStatus', label: 'Result', render: (row) => <Chip size="small" color={STAGE_COLOR[row.overallStatus]} label={OVERALL_LABEL[row.overallStatus]} /> },
  { key: 'paymentReference', label: 'Payment Reference', render: (row) => row.paymentReference },
  { key: 'correlationId', label: 'Correlation ID', render: (row) => row.correlationId },
  { key: 'traceId', label: 'Trace ID', render: (row) => row.traceId ?? '—' },
  { key: 'duration', label: 'Duration', align: 'right', render: (row) => formatDuration(row.durationMillis) },
  { key: 'startedAt', label: 'Started', render: (row) => formatTimestamp(row.startedAt) },
  { key: 'completedAt', label: 'Completed', render: (row) => formatTimestamp(row.completedAt) },
]

export default function E2EPage() {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [activeRunId, setActiveRunId] = useState<string | null>(null)
  const startRun = useStartE2ERun()
  const activeRun = useE2ERun(activeRunId)
  const history = useE2EHistory()

  function handleConfirmRun() {
    setConfirmOpen(false)
    startRun.mutate(undefined, {
      onSuccess: (result) => setActiveRunId(result.runId),
    })
  }

  const run = activeRun.data

  useEffect(() => {
    if (run && run.overallStatus !== 'RUNNING') {
      history.refetch()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run?.overallStatus])

  return (
    <PageContainer>
      <PageHeader
        title="E2E Payment Flow"
        description="Run one real, bounded payment through the real platform (Gateway -> Authentication -> Validation -> Routing -> Payment -> Audit -> Notification -> Reconciliation -> Reporting) and watch its real, live stage state."
      />

      <Box>
        <Button
          variant="contained"
          size="large"
          startIcon={<PlayCircleOutlineIcon />}
          disabled={run?.overallStatus === 'RUNNING'}
          onClick={() => setConfirmOpen(true)}
        >
          Run Complete Payment Flow
        </Button>
      </Box>

      <ConfirmationDialog
        open={confirmOpen}
        title="Run a real end-to-end payment?"
        message="This submits one real, small (1.00 USD) test payment through the real API Gateway, using a real, short-lived test API key. It is a genuine transaction against the real platform, not a simulation - it will appear in Payments/Audit/Notification like any other real payment."
        confirmLabel="Run it"
        onConfirm={handleConfirmRun}
        onCancel={() => setConfirmOpen(false)}
      />

      {startRun.isError && <ErrorState message={toApiError(startRun.error).message} />}
      {activeRun.isError && <ErrorState message={toApiError(activeRun.error).message} />}

      {run && (
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Chip color={STAGE_COLOR[run.overallStatus]} label={OVERALL_LABEL[run.overallStatus]} />
            {run.overallStatus === 'RUNNING' && <LinearProgress sx={{ width: 160 }} />}
          </Stack>

          <Stack direction="row" spacing={4} flexWrap="wrap" useFlexGap>
            <Stack>
              <Typography variant="caption" color="text.secondary">Payment Reference</Typography>
              <Typography variant="body2" fontFamily="monospace">{run.paymentReference}</Typography>
            </Stack>
            <Stack>
              <Typography variant="caption" color="text.secondary">Correlation ID</Typography>
              <Typography variant="body2" fontFamily="monospace">{run.correlationId}</Typography>
            </Stack>
            <Stack>
              <Typography variant="caption" color="text.secondary">Trace ID</Typography>
              <Typography variant="body2" fontFamily="monospace">{run.traceId ?? '—'}</Typography>
            </Stack>
            <Stack>
              <Typography variant="caption" color="text.secondary">Duration</Typography>
              <Typography variant="body2">{formatDuration(run.durationMillis)}</Typography>
            </Stack>
          </Stack>

          {run.failureReason && (
            <Alert severity={run.overallStatus === 'TIMEOUT' ? 'warning' : 'error'}>{run.failureReason}</Alert>
          )}

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {run.stages.map((stage) => (
              <Chip
                key={stage.name}
                color={STAGE_COLOR[stage.status]}
                label={`${stage.name}: ${OVERALL_LABEL[stage.status]}`}
                title={stage.detail ?? undefined}
                variant={stage.status === 'PENDING' ? 'outlined' : 'filled'}
              />
            ))}
          </Stack>

          <Stack spacing={0.5}>
            {run.stages.map((stage) => (
              <Typography key={stage.name} variant="caption" color="text.secondary">
                <strong>{stage.name}:</strong> {stage.detail ?? '—'}
              </Typography>
            ))}
          </Stack>
        </Stack>
      )}

      <Typography variant="h2" component="h2">
        Run History
      </Typography>
      {history.isError && <ErrorState message={toApiError(history.error).message} onRetry={() => history.refetch()} />}
      {history.data && history.data.length === 0 && (
        <EmptyState title="No runs yet" message="Click “Run Complete Payment Flow” above to run the first real E2E payment." />
      )}
      {history.data && history.data.length > 0 && (
        <DataTable
          rows={history.data}
          columns={historyColumns}
          getRowKey={(row) => row.runId}
          emptyTitle="No runs yet"
          emptyMessage="Click “Run Complete Payment Flow” above to run the first real E2E payment."
        />
      )}
    </PageContainer>
  )
}
