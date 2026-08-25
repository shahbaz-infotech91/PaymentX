/**
 * Phase 4.7 - the "/ai-agents/execute" page: a single, generic execution form driven by the real
 * Agent Registry (useAgentList), not five separate hardcoded per-agent implementations. Every
 * currently registered agent's own allowed-tools contract makes paymentReference an OPTIONAL
 * field (see AgentExecuteRequest.java) - this form does not hide/require it per agent, it is
 * always available and always optional, matching the real backend contract rather than an
 * assumed one. Submitting calls the real backend (POST /api/v1/agents/execute -> Agent
 * Orchestrator -> the selected agent's real AgentToolPolicy/MCP chain) and renders the REAL
 * response - nothing here is a frontend-fabricated answer, trace, or source list.
 */
import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { useAgentList, useExecuteAgent } from '../hooks/useAgents'
import { classifyAgentError } from '../services/agentService'

const SUCCESS_STATUSES = new Set(['SUCCESS'])
const NEUTRAL_STATUSES = new Set(['INSUFFICIENT_CONTEXT', 'REFUSED'])

function statusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  if (SUCCESS_STATUSES.has(status)) return 'success'
  if (NEUTRAL_STATUSES.has(status)) return 'warning'
  if (status === 'DENIED' || status === 'FAILED' || status === 'TIMEOUT' || status === 'MAX_ITERATIONS') return 'error'
  return 'default'
}

export default function AiAgentExecutePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const preselectedAgentId = (location.state as { agentId?: string } | null)?.agentId ?? ''

  const agents = useAgentList()
  const executeMutation = useExecuteAgent()

  const [agentId, setAgentId] = useState(preselectedAgentId)
  const [paymentReference, setPaymentReference] = useState('')
  const [userQuery, setUserQuery] = useState('')

  const canSubmit = useMemo(() => agentId !== '' && userQuery.trim() !== '', [agentId, userQuery])

  function handleExecute() {
    // Guards against a double-click/double-submit causing a second, duplicate execution -
    // isPending is the real in-flight state of this exact mutation, not a UI-only flag.
    if (!canSubmit || executeMutation.isPending) return
    executeMutation.mutate({
      agentId,
      userQuery: userQuery.trim(),
      paymentReference: paymentReference.trim() || undefined,
    })
  }

  function startNewExecution() {
    executeMutation.reset()
    setUserQuery('')
  }

  const result = executeMutation.data

  return (
    <PageContainer>
      <PageHeader
        title="Execute Agent"
        description="Run any registered PaymentX agent and see its real, live response."
      />

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack spacing={2.5}>
          {agents.isLoading && <LoadingState message="Loading agents…" />}
          {agents.isError && <ErrorState message={classifyAgentError(agents.error)} onRetry={() => agents.refetch()} />}

          {agents.data && (
            <>
              <TextField
                select
                label="Agent"
                value={agentId}
                onChange={(e) => setAgentId(e.target.value)}
                disabled={executeMutation.isPending}
                fullWidth
              >
                <MenuItem value="" disabled>
                  Select an agent…
                </MenuItem>
                {agents.data.map((agent) => (
                  <MenuItem key={agent.agentId} value={agent.agentId} disabled={!agent.enabled}>
                    {agent.name} {!agent.enabled && '(disabled)'}
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                label="Payment Reference (optional)"
                value={paymentReference}
                onChange={(e) => setPaymentReference(e.target.value)}
                disabled={executeMutation.isPending}
                helperText="Only relevant if your question is about a specific payment. Leave blank for a general question."
                fullWidth
              />

              <TextField
                label="Question"
                value={userQuery}
                onChange={(e) => setUserQuery(e.target.value)}
                disabled={executeMutation.isPending}
                multiline
                minRows={3}
                fullWidth
                required
              />

              <Stack direction="row" spacing={1.5}>
                <Button
                  variant="contained"
                  startIcon={<PlayArrowIcon />}
                  onClick={handleExecute}
                  disabled={!canSubmit || executeMutation.isPending}
                >
                  {executeMutation.isPending ? 'Executing…' : 'Execute Agent'}
                </Button>
                {(result || executeMutation.isError) && (
                  <Button variant="outlined" onClick={startNewExecution} disabled={executeMutation.isPending}>
                    New Execution
                  </Button>
                )}
              </Stack>
            </>
          )}

          {executeMutation.isPending && <LoadingState message="Waiting for the agent's real response — this can take up to a minute…" />}

          {executeMutation.isError && (
            <ErrorState title="Execution failed" message={classifyAgentError(executeMutation.error)} />
          )}

          {result && (
            <Stack spacing={2}>
              <Divider />
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="h6">Result</Typography>
                <Chip label={result.status} color={statusColor(result.status)} size="small" />
                {result.provider && (
                  <Chip
                    label={result.fallbackUsed ? `${result.provider} (fallback)` : result.provider}
                    color={result.fallbackUsed ? 'warning' : 'default'}
                    variant="outlined"
                    size="small"
                  />
                )}
              </Stack>
              {result.fallbackUsed && (
                <Alert severity="warning" sx={{ mt: 1 }}>
                  The primary LLM provider was unavailable, so this answer was produced by the automatic fallback
                  provider{result.fallbackReason ? ` (reason: ${result.fallbackReason})` : ''}.
                </Alert>
              )}

              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Answer
                </Typography>
                <Typography variant="body2" component="pre" sx={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit', m: 0 }}>
                  {result.answer}
                </Typography>
              </Box>

              <Stack direction="row" spacing={3} flexWrap="wrap">
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Execution ID
                  </Typography>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                    {result.executionId ?? '—'}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Correlation ID
                  </Typography>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                    {result.correlationId ?? '—'}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Duration
                  </Typography>
                  <Typography variant="body2">{(result.durationMs / 1000).toFixed(1)}s</Typography>
                </Box>
              </Stack>

              {result.executionMetadata && (
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Iterations: {result.executionMetadata.iterations} · Tool calls: {result.executionMetadata.toolCallCount} · RAG used:{' '}
                    {result.executionMetadata.ragUsed ? 'Yes' : 'No'}
                  </Typography>
                </Box>
              )}

              {result.toolsCalled.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Tools Called
                  </Typography>
                  <Stack spacing={0.5}>
                    {result.toolsCalled.map((tool, index) => (
                      <Stack key={`${tool.tool}-${index}`} direction="row" spacing={1} alignItems="center">
                        <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                          {tool.tool}
                        </Typography>
                        <Chip size="small" label={tool.status} color={tool.status === 'SUCCESS' ? 'success' : 'error'} />
                      </Stack>
                    ))}
                  </Stack>
                </Box>
              )}

              {result.sources.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    RAG Sources
                  </Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                    {result.sources.map((source, index) => (
                      <Chip key={`${source.source}-${index}`} size="small" variant="outlined" label={source.source ?? 'unknown'} />
                    ))}
                  </Stack>
                </Box>
              )}

              {result.executionId && (
                <Alert severity="info" sx={{ mt: 1 }}>
                  This execution is now recorded in{' '}
                  <Button size="small" onClick={() => navigate('/ai-agents/history', { state: { executionId: result.executionId } })}>
                    Execution History
                  </Button>
                </Alert>
              )}
            </Stack>
          )}
        </Stack>
      </Paper>
    </PageContainer>
  )
}
