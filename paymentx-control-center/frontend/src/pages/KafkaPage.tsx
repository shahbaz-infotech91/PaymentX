/**
 * ENGLISH: The "/kafka" page - real topics (with real DLT/retry
 * classification from the platform's actual ".DLT"/".retry" naming
 * convention) and real consumer groups from the live broker, via the
 * backend's AdminClient-backed monitoring endpoints. Selecting a
 * consumer group loads its real per-partition lag (committed vs. log-
 * end offset, computed fresh on every request). Why it exists:
 * required route "/kafka", now wired to the real Phase 2 backend. How
 * it will communicate with the backend: via useKafkaTopics/
 * useKafkaConsumerGroups/useKafkaConsumerGroupLag -> kafkaService.ts
 * -> GET /api/v1/kafka/*.
 *
 * HINGLISH: "/kafka" page - real topics (platform ke actual
 * ".DLT"/".retry" naming convention se real DLT/retry classification
 * ke saath) aur live broker se real consumer groups, backend ke
 * AdminClient-backed monitoring endpoints ke through. Ek consumer
 * group select karne se uska real per-partition lag load hota hai
 * (committed vs log-end offset, har request par fresh compute kiya
 * gaya). Ye dashboard me kyu hai: required route "/kafka", ab real
 * Phase 2 backend se wired hai. Backend se kaise connect hogi:
 * useKafkaTopics/useKafkaConsumerGroups/useKafkaConsumerGroupLag ->
 * kafkaService.ts -> GET /api/v1/kafka/* ke through.
 */
import { useState } from 'react'
import { Chip, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { MetricCard } from '../components/MetricCard'
import { useKafkaTopics, useKafkaConsumerGroups, useKafkaConsumerGroupLag, useKafkaTopicThroughput } from '../hooks/useKafkaMonitoring'
import { toApiError } from '../api/axiosClient'
import type { KafkaTopicInfo, KafkaPartitionLag } from '../services/kafkaService'

const topicColumns: DataTableColumn<KafkaTopicInfo>[] = [
  { key: 'name', label: 'Topic', render: (row) => row.name },
  { key: 'partitions', label: 'Partitions', align: 'right', render: (row) => row.partitionCount },
  { key: 'replication', label: 'Replication', align: 'right', render: (row) => row.replicationFactor },
  { key: 'produced', label: 'Produced', align: 'right', render: (row) => row.messageCount.toLocaleString() },
  { key: 'errors', label: 'Errors', align: 'right', render: (row) => (row.errorCount != null ? row.errorCount.toLocaleString() : '—') },
  {
    key: 'classification',
    label: 'Classification',
    render: (row) => (
      <Stack direction="row" spacing={0.5}>
        {row.deadLetterTopic && <Chip size="small" color="error" label="DLT" />}
        {row.retryTopic && <Chip size="small" color="warning" label="retry" />}
        {!row.deadLetterTopic && !row.retryTopic && '—'}
      </Stack>
    ),
  },
]

const lagColumns: DataTableColumn<KafkaPartitionLag>[] = [
  { key: 'topic', label: 'Topic', render: (row) => row.topic },
  { key: 'partition', label: 'Partition', align: 'right', render: (row) => row.partition },
  { key: 'committed', label: 'Committed', align: 'right', render: (row) => row.committedOffset },
  { key: 'end', label: 'End Offset', align: 'right', render: (row) => row.endOffset },
  { key: 'lag', label: 'Lag', align: 'right', render: (row) => row.lag },
]

export default function KafkaPage() {
  const topics = useKafkaTopics()
  const groups = useKafkaConsumerGroups()
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null)
  const lag = useKafkaConsumerGroupLag(selectedGroup)
  const [throughputTopic, setThroughputTopic] = useState<string | null>(null)
  const throughput = useKafkaTopicThroughput(throughputTopic)

  const selectedGroupSummary = groups.data?.find((g) => g.groupId === selectedGroup)

  return (
    <PageContainer>
      <PageHeader title="Kafka" description="Real topics, partitions, throughput, and consumer group lag from the live PaymentX Kafka cluster." />

      <Typography variant="h2" component="h2">
        Topics
      </Typography>
      {topics.isLoading && <LoadingState message="Loading topics…" />}
      {topics.isError && <ErrorState message={toApiError(topics.error).message} onRetry={() => topics.refetch()} />}
      {topics.data && (
        <DataTable
          rows={topics.data}
          columns={topicColumns}
          getRowKey={(row) => row.name}
          emptyTitle="No topics found"
          emptyMessage="The broker reported zero topics."
        />
      )}

      <Typography variant="h2" component="h2">
        Messages/sec
      </Typography>
      {topics.data && (
        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
          <TextField
            select
            size="small"
            label="Topic"
            value={throughputTopic ?? ''}
            onChange={(e) => setThroughputTopic(e.target.value || null)}
            sx={{ minWidth: 320 }}
          >
            {topics.data.map((t) => (
              <MenuItem key={t.name} value={t.name}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          {throughputTopic && (
            <Chip
              label="Measure now (1s sample)"
              color="primary"
              onClick={() => throughput.refetch()}
              disabled={throughput.isFetching}
            />
          )}
          {throughput.isFetching && <LoadingState message="Sampling throughput (1s)…" />}
          {throughput.data && (
            <MetricCard label={throughput.data.topic} value={`${throughput.data.messagesPerSecond.toFixed(2)} msg/s`} helperText={`${throughput.data.messagesInWindow} messages over ${throughput.data.windowMillis} ms`} />
          )}
        </Stack>
      )}

      <Typography variant="h2" component="h2">
        Consumer Groups &amp; Lag
      </Typography>
      {groups.isLoading && <LoadingState message="Loading consumer groups…" />}
      {groups.isError && <ErrorState message={toApiError(groups.error).message} onRetry={() => groups.refetch()} />}
      {groups.data && (
        <TextField
          select
          size="small"
          label="Consumer group"
          value={selectedGroup ?? ''}
          onChange={(e) => setSelectedGroup(e.target.value || null)}
          sx={{ maxWidth: 360 }}
        >
          {groups.data.map((group) => (
            <MenuItem key={group.groupId} value={group.groupId}>
              {group.groupId} ({group.state}, {group.memberCount} members)
            </MenuItem>
          ))}
        </TextField>
      )}
      {selectedGroupSummary && (
        <MetricCard label="Consumed (total committed offset)" value={selectedGroupSummary.totalCommittedOffset.toLocaleString()} />
      )}
      {selectedGroup && lag.isLoading && <LoadingState message="Computing lag…" />}
      {selectedGroup && lag.isError && <ErrorState message={toApiError(lag.error).message} onRetry={() => lag.refetch()} />}
      {selectedGroup && lag.data && (
        <DataTable
          rows={lag.data}
          columns={lagColumns}
          getRowKey={(row) => `${row.topic}-${row.partition}`}
          emptyTitle="No offsets committed"
          emptyMessage="This consumer group has not committed any offsets yet."
        />
      )}
    </PageContainer>
  )
}
