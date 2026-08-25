/**
 * Phase 4.7 - the "/ai-agents/history" page: paginated, filterable execution history sourced
 * entirely from the real audit trail Agent Orchestrator already writes on every execution (see
 * backend's AgentExecutionRepository javadoc) - never a frontend-only or fabricated record.
 * Clicking a row opens a Drawer with the full stored detail (Phase 9's "Execution Details"),
 * fetched fresh from GET /api/v1/agents/executions/{id} rather than reusing whatever was in the
 * list row, so the detail view is always the real, complete stored record.
 */
import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { FilterBar } from '../components/FilterBar'
import { useAgentList, useExecutionDetail, useExecutionHistory } from '../hooks/useAgents'
import { classifyAgentError } from '../services/agentService'
import { formatTimestamp } from '../utils/formatters'
import type { AgentExecutionSummary } from '../types/agent'

const PAGE_SIZE = 20

const OUTCOME_OPTIONS = ['SUCCESS', 'INSUFFICIENT_CONTEXT', 'REFUSED', 'DENIED', 'MAX_ITERATIONS', 'TIMEOUT', 'FAILED']

function outcomeColor(outcome: string | null): 'success' | 'warning' | 'error' | 'default' {
  if (outcome === 'SUCCESS') return 'success'
  if (outcome === 'INSUFFICIENT_CONTEXT' || outcome === 'REFUSED') return 'warning'
  if (outcome === 'DENIED' || outcome === 'FAILED' || outcome === 'TIMEOUT' || outcome === 'MAX_ITERATIONS') return 'error'
  return 'default'
}

export default function AiAgentHistoryPage() {
  const location = useLocation()
  const preselectedExecutionId = (location.state as { executionId?: string } | null)?.executionId ?? null

  const agents = useAgentList()
  const [page, setPage] = useState(0)
  const [agentFilter, setAgentFilter] = useState('')
  const [outcomeFilter, setOutcomeFilter] = useState('')
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(preselectedExecutionId)

  const filter = {
    agentId: agentFilter || undefined,
    outcome: outcomeFilter || undefined,
  }
  const history = useExecutionHistory(page, PAGE_SIZE, filter)
  const detail = useExecutionDetail(selectedExecutionId)

  useEffect(() => {
    if (preselectedExecutionId) setSelectedExecutionId(preselectedExecutionId)
  }, [preselectedExecutionId])

  const columns: DataTableColumn<AgentExecutionSummary>[] = [
    { key: 'timestamp', label: 'Time', render: (row) => formatTimestamp(row.timestamp) },
    { key: 'agentId', label: 'Agent', render: (row) => row.agentId ?? '—' },
    { key: 'userQuery', label: 'Query', render: (row) => (row.userQuery ? truncate(row.userQuery, 60) : '—') },
    { key: 'paymentReference', label: 'Payment Reference', render: (row) => row.paymentReference ?? '—' },
    {
      key: 'outcome',
      label: 'Status',
      render: (row) => <Chip size="small" label={row.outcome ?? 'UNKNOWN'} color={outcomeColor(row.outcome)} />,
    },
    { key: 'durationMs', label: 'Duration', render: (row) => (row.durationMs != null ? `${(row.durationMs / 1000).toFixed(1)}s` : '—') },
    { key: 'toolCallCount', label: 'Tools', render: (row) => row.toolCallCount ?? '—' },
    {
      key: 'provider',
      label: 'Provider',
      render: (row) =>
        row.provider ? (
          <Chip size="small" variant="outlined" color={row.fallbackUsed ? 'warning' : 'default'} label={row.fallbackUsed ? `${row.provider} (fallback)` : row.provider} />
        ) : (
          '—'
        ),
    },
  ]

  return (
    <PageContainer>
      <PageHeader title="Execution History" description="Every real agent execution, sourced from the real audit trail." />

      <FilterBar>
        <TextField
          select
          size="small"
          label="Agent"
          value={agentFilter}
          onChange={(e) => {
            setAgentFilter(e.target.value)
            setPage(0)
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">All agents</MenuItem>
          {agents.data?.map((agent) => (
            <MenuItem key={agent.agentId} value={agent.agentId}>
              {agent.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Status"
          value={outcomeFilter}
          onChange={(e) => {
            setOutcomeFilter(e.target.value)
            setPage(0)
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {OUTCOME_OPTIONS.map((outcome) => (
            <MenuItem key={outcome} value={outcome}>
              {outcome}
            </MenuItem>
          ))}
        </TextField>
      </FilterBar>

      {history.isLoading && <LoadingState message="Loading execution history…" />}
      {history.isError && <ErrorState message={classifyAgentError(history.error)} onRetry={() => history.refetch()} />}
      {history.data && (
        <>
          <DataTable
            rows={history.data.content}
            columns={columns}
            getRowKey={(row) => row.executionId}
            onRowClick={(row) => setSelectedExecutionId(row.executionId)}
            emptyTitle="No executions yet"
            emptyMessage="Executions run from the Execute Agent page will appear here."
          />
          <Pager page={history.data.page} size={history.data.size} totalElements={history.data.totalElements} onPageChange={setPage} />
        </>
      )}

      <Drawer anchor="right" open={selectedExecutionId !== null} onClose={() => setSelectedExecutionId(null)}>
        <Box sx={{ width: 480, p: 3 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="h6">Execution Details</Typography>
            <IconButton onClick={() => setSelectedExecutionId(null)}>
              <CloseIcon />
            </IconButton>
          </Stack>

          {detail.isLoading && <LoadingState message="Loading execution detail…" />}
          {detail.isError && <ErrorState message={classifyAgentError(detail.error)} />}

          {detail.data && (
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip size="small" label={detail.data.outcome ?? 'UNKNOWN'} color={outcomeColor(detail.data.outcome)} />
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                  {detail.data.agentId}
                </Typography>
              </Stack>

              <DetailField label="Execution ID" value={detail.data.executionId} monospace />
              <DetailField label="Correlation ID" value={detail.data.correlationId ?? '—'} monospace />
              <DetailField label="Started At" value={detail.data.startedAt ? formatTimestamp(detail.data.startedAt) : '—'} />
              <DetailField label="Completed At" value={detail.data.completedAt ? formatTimestamp(detail.data.completedAt) : '—'} />
              <DetailField label="Duration" value={detail.data.durationMs != null ? `${(detail.data.durationMs / 1000).toFixed(1)}s` : '—'} />

              <Divider />

              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  User Query
                </Typography>
                <Typography variant="body2">{detail.data.userQuery ?? '—'}</Typography>
              </Box>

              {detail.data.paymentReference && <DetailField label="Payment Reference" value={detail.data.paymentReference} monospace />}

              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Agent Response
                </Typography>
                <Typography variant="body2" component="pre" sx={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit', m: 0 }}>
                  {detail.data.answer ?? 'Not available for this execution.'}
                </Typography>
              </Box>

              {detail.data.toolsCalled.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Tools Called
                  </Typography>
                  <Stack spacing={0.5}>
                    {detail.data.toolsCalled.map((tool, index) => (
                      <Stack key={`${tool.tool}-${index}`} direction="row" spacing={1} alignItems="center">
                        <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                          {tool.tool}
                        </Typography>
                        <Chip size="small" label={tool.status ?? 'UNKNOWN'} color={tool.status === 'SUCCESS' ? 'success' : 'error'} />
                      </Stack>
                    ))}
                  </Stack>
                </Box>
              )}

              {detail.data.ragSources.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    RAG Sources
                  </Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                    {detail.data.ragSources.map((source, index) => (
                      <Chip key={`${source}-${index}`} size="small" variant="outlined" label={source} />
                    ))}
                  </Stack>
                </Box>
              )}

              <Box>
                <Typography variant="caption" color="text.secondary">
                  Iterations: {detail.data.iterations ?? '—'} · RAG used: {detail.data.ragUsed ? 'Yes' : 'No'} · Provider:{' '}
                  {detail.data.provider ?? '—'}
                  {detail.data.fallbackUsed ? ' (fallback used)' : ''}
                </Typography>
              </Box>

              {detail.data.fallbackUsed && detail.data.fallbackReason && (
                <Box>
                  <Typography variant="subtitle2" color="warning.main">
                    Fallback Reason
                  </Typography>
                  <Typography variant="body2">{detail.data.fallbackReason}</Typography>
                </Box>
              )}

              {detail.data.error && (
                <Box>
                  <Typography variant="subtitle2" color="error">
                    Error
                  </Typography>
                  <Typography variant="body2">{detail.data.error}</Typography>
                </Box>
              )}
            </Stack>
          )}
        </Box>
      </Drawer>
    </PageContainer>
  )
}

function DetailField({ label, value, monospace }: { label: string; value: string; monospace?: boolean }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={monospace ? { fontFamily: 'monospace' } : undefined}>
        {value}
      </Typography>
    </Box>
  )
}

function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max)}…` : value
}
