/**
 * ENGLISH: The "/rabbitmq" page - real broker overview, real
 * connections, and real queues (with real ready/unacknowledged counts
 * and dlq/retry classification) from RabbitMQ's Management API. Note
 * shown honestly on this page: this platform's actual RabbitMQ usage
 * is infrastructure-only (verified during earlier PaymentX validation
 * work) - a near-empty broker state is the real, honest state, not a
 * broken integration. Why it exists: required route "/rabbitmq", now
 * wired to the real Phase 2 backend. How it will communicate with the
 * backend: via useRabbitMqOverview/useRabbitMqConnections/
 * useRabbitMqQueues -> rabbitMqService.ts -> GET /api/v1/rabbitmq/*.
 *
 * HINGLISH: "/rabbitmq" page - RabbitMQ ke Management API se real
 * broker overview, real connections, aur real queues (real
 * ready/unacknowledged counts aur dlq/retry classification ke saath).
 * Is page par honestly dikhaya gaya note: is platform ka actual
 * RabbitMQ usage infrastructure-only hai (pehle ke PaymentX validation
 * work me verify kiya gaya) - ek near-empty broker state real, honest
 * state hai, koi broken integration nahi. Ye dashboard me kyu hai:
 * required route "/rabbitmq", ab real Phase 2 backend se wired hai.
 * Backend se kaise connect hogi: useRabbitMqOverview/
 * useRabbitMqConnections/useRabbitMqQueues -> rabbitMqService.ts ->
 * GET /api/v1/rabbitmq/* ke through.
 */
import { Alert, Chip, Grid2 as Grid, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { MetricCard } from '../components/MetricCard'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { useRabbitMqOverview, useRabbitMqConnections, useRabbitMqQueues, useRabbitMqBindings } from '../hooks/useRabbitMqMonitoring'
import { toApiError } from '../api/axiosClient'
import type { RabbitMqBindingSummary, RabbitMqConnectionSummary, RabbitMqQueueSummary } from '../services/rabbitMqService'

const connectionColumns: DataTableColumn<RabbitMqConnectionSummary>[] = [
  { key: 'name', label: 'Name', render: (row) => row.name ?? '—' },
  { key: 'state', label: 'State', render: (row) => row.state ?? '—' },
  { key: 'peer', label: 'Peer', render: (row) => (row.peerHost ? `${row.peerHost}:${row.peerPort ?? ''}` : '—') },
  { key: 'user', label: 'User', render: (row) => row.user ?? '—' },
]

const queueColumns: DataTableColumn<RabbitMqQueueSummary>[] = [
  { key: 'name', label: 'Queue', render: (row) => row.name },
  {
    key: 'classification',
    label: 'Type',
    render: (row) => (row.deadLetterQueue ? <Chip size="small" color="error" label="DLQ" /> : row.retryQueue ? <Chip size="small" color="warning" label="retry" /> : '—'),
  },
  { key: 'consumers', label: 'Consumers', align: 'right', render: (row) => row.consumers },
  { key: 'ready', label: 'Ready', align: 'right', render: (row) => row.messagesReady },
  { key: 'unacked', label: 'Unacked', align: 'right', render: (row) => row.messagesUnacknowledged },
  { key: 'publishRate', label: 'Publish/s', align: 'right', render: (row) => row.publishRatePerSecond.toFixed(2) },
  { key: 'consumeRate', label: 'Consume/s', align: 'right', render: (row) => row.deliverRatePerSecond.toFixed(2) },
]

const bindingColumns: DataTableColumn<RabbitMqBindingSummary>[] = [
  { key: 'source', label: 'Source', render: (row) => row.source || '(default exchange)' },
  { key: 'destination', label: 'Destination', render: (row) => row.destination },
  { key: 'destinationType', label: 'Type', render: (row) => row.destinationType },
  { key: 'routingKey', label: 'Routing Key', render: (row) => row.routingKey || '—' },
]

export default function RabbitMqPage() {
  const overview = useRabbitMqOverview()
  const connections = useRabbitMqConnections()
  const queues = useRabbitMqQueues()
  const bindings = useRabbitMqBindings()

  return (
    <PageContainer>
      <PageHeader title="RabbitMQ" description="Real broker overview, connections, and queues from the RabbitMQ Management API." />
      <Alert severity="info">
        This platform's real RabbitMQ usage is infrastructure-only - no PaymentX service currently publishes or
        consumes through it. A near-empty broker state below is the honest, real state.
      </Alert>

      {overview.isLoading && <LoadingState message="Loading RabbitMQ overview…" />}
      {overview.isError && <ErrorState message={toApiError(overview.error).message} onRetry={() => overview.refetch()} />}
      {overview.data && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Version" value={overview.data.rabbitmqVersion ?? '—'} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Connections" value={overview.data.totalConnections ?? 0} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Channels" value={overview.data.totalChannels ?? 0} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Queues" value={overview.data.totalQueues ?? 0} />
          </Grid>
        </Grid>
      )}

      <Typography variant="h2" component="h2">
        Connections
      </Typography>
      {connections.data && (
        <DataTable
          rows={connections.data}
          columns={connectionColumns}
          getRowKey={(row) => row.name ?? `${row.peerHost ?? 'unknown'}:${row.peerPort ?? 0}:${row.connectedAtEpochMillis ?? 0}`}
          emptyTitle="No active connections"
          emptyMessage="No client is currently connected to this RabbitMQ broker."
        />
      )}

      <Typography variant="h2" component="h2">
        Queues
      </Typography>
      {queues.data && (
        <DataTable
          rows={queues.data}
          columns={queueColumns}
          getRowKey={(row) => `${row.vhost}-${row.name}`}
          emptyTitle="No queues declared"
          emptyMessage="This broker has no declared queues."
        />
      )}

      <Typography variant="h2" component="h2">
        Bindings
      </Typography>
      {bindings.isLoading && <LoadingState message="Loading bindings…" />}
      {bindings.isError && <ErrorState message={toApiError(bindings.error).message} onRetry={() => bindings.refetch()} />}
      {bindings.data && (
        <DataTable
          rows={bindings.data}
          columns={bindingColumns}
          getRowKey={(row) => `${row.vhost}-${row.source}-${row.destination}-${row.routingKey}`}
          emptyTitle="No bindings declared"
          emptyMessage="This broker has no declared bindings."
        />
      )}
    </PageContainer>
  )
}
