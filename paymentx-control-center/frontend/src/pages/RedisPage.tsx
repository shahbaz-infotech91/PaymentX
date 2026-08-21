/**
 * ENGLISH: The "/redis" page - real connection health (PING latency,
 * version, role, uptime), real memory usage, and real key population
 * by category (a bounded, live SCAN grouped by each key's real
 * two-segment prefix - never a full key dump or any key's value). Why
 * it exists: required route "/redis", now wired to the real Phase 2
 * backend. How it will communicate with the backend: via
 * useRedisHealth/useRedisMemory/useRedisKeyspace -> redisService.ts
 * -> GET /api/v1/redis/*.
 *
 * HINGLISH: "/redis" page - real connection health (PING latency,
 * version, role, uptime), real memory usage, aur real key population
 * category ke hisaab se (ek bounded, live SCAN jo har key ke real
 * two-segment prefix se group kiya gaya - kabhi ek full key dump ya
 * kisi key ki value nahi). Ye dashboard me kyu hai: required route
 * "/redis", ab real Phase 2 backend se wired hai. Backend se kaise
 * connect hogi: useRedisHealth/useRedisMemory/useRedisKeyspace ->
 * redisService.ts -> GET /api/v1/redis/* ke through.
 */
import { Grid2 as Grid, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { MetricCard } from '../components/MetricCard'
import { StatusBadge } from '../components/StatusBadge'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { useRedisHealth, useRedisMemory, useRedisKeyspace, useRedisSamples } from '../hooks/useRedisMonitoring'
import { toApiError } from '../api/axiosClient'
import { formatBytes } from '../utils/formatters'
import type { RedisKeyCategoryCount, RedisKeySample } from '../services/redisService'

const categoryColumns: DataTableColumn<RedisKeyCategoryCount>[] = [
  { key: 'prefix', label: 'Key Prefix', render: (row) => row.prefix },
  { key: 'count', label: 'Count', align: 'right', render: (row) => row.count.toLocaleString() },
]

const sampleColumns: DataTableColumn<RedisKeySample>[] = [
  { key: 'key', label: 'Key', render: (row) => row.key },
  { key: 'ttl', label: 'TTL', align: 'right', render: (row) => (row.ttlSeconds != null ? `${row.ttlSeconds.toLocaleString()}s` : 'no expiry') },
]

export default function RedisPage() {
  const health = useRedisHealth()
  const memory = useRedisMemory()
  const keyspace = useRedisKeyspace()
  const samples = useRedisSamples()

  return (
    <PageContainer>
      <PageHeader title="Redis" description="Real cache health, memory usage, and key population by category." />

      {health.isLoading && <LoadingState message="Checking Redis connection…" />}
      {health.isError && <ErrorState message={toApiError(health.error).message} onRetry={() => health.refetch()} />}
      {health.data && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Connection" value={<StatusBadge status={health.data.reachable ? 'UP' : 'DOWN'} />} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Ping Latency" value={`${health.data.pingLatencyMillis} ms`} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Version" value={health.data.redisVersion ?? '—'} helperText={health.data.role ?? undefined} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <MetricCard label="Connected Clients" value={health.data.connectedClients ?? '—'} />
          </Grid>
        </Grid>
      )}

      {memory.data && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <MetricCard label="Used Memory" value={memory.data.usedMemoryHuman ?? formatBytes(memory.data.usedMemoryBytes)} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <MetricCard label="Max Memory" value={memory.data.maxMemoryBytes > 0 ? formatBytes(memory.data.maxMemoryBytes) : 'Unbounded'} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <MetricCard label="Fragmentation Ratio" value={memory.data.memoryFragmentationRatio.toFixed(2)} />
          </Grid>
        </Grid>
      )}

      <Typography variant="h2" component="h2">
        Keyspace
      </Typography>
      {keyspace.isLoading && <LoadingState message="Scanning keyspace…" />}
      {keyspace.isError && <ErrorState message={toApiError(keyspace.error).message} onRetry={() => keyspace.refetch()} />}
      {keyspace.data && (
        <>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <MetricCard label="Total Keys" value={keyspace.data.totalKeyCount.toLocaleString()} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <MetricCard
                label="Hit Rate"
                value={keyspace.data.hitRatePercent !== null ? `${keyspace.data.hitRatePercent.toFixed(1)}%` : '—'}
                helperText={`${keyspace.data.keyspaceHits.toLocaleString()} hits / ${keyspace.data.keyspaceMisses.toLocaleString()} misses`}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <MetricCard
                label="Miss Rate"
                value={keyspace.data.hitRatePercent !== null ? `${(100 - keyspace.data.hitRatePercent).toFixed(1)}%` : '—'}
              />
            </Grid>
          </Grid>
          <DataTable
            rows={keyspace.data.categories}
            columns={categoryColumns}
            getRowKey={(row) => row.prefix}
            emptyTitle="No keys found"
            emptyMessage="No keys were found during the bounded scan."
          />
        </>
      )}

      <Typography variant="h2" component="h2">
        Keys (bounded sample, TTL where relevant)
      </Typography>
      {samples.isLoading && <LoadingState message="Sampling keys…" />}
      {samples.isError && <ErrorState message={toApiError(samples.error).message} onRetry={() => samples.refetch()} />}
      {samples.data && (
        <DataTable
          rows={samples.data}
          columns={sampleColumns}
          getRowKey={(row) => row.key}
          emptyTitle="No keys sampled"
          emptyMessage="No keys were found during the bounded sample scan."
        />
      )}
    </PageContainer>
  )
}
