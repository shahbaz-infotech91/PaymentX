/**
 * Phase 4.7 - the "/ai-agents" Dashboard page: every real, currently-registered PaymentX agent
 * (Error Analyzer, Knowledge Assistant, Database Analysis, Fraud Detection, Reconciliation - plus
 * the platform "default" agent), fetched live from Agent Orchestrator's own AgentRegistry via
 * GET /api/v1/agents - never a hardcoded five-agent list. A disabled agent (enabled=false) would
 * render here honestly too; nothing here is faked.
 */
import { Card, CardContent, Chip, Grid2 as Grid, Stack, Typography, Button } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { EmptyState } from '../components/EmptyState'
import { useAgentList } from '../hooks/useAgents'
import { classifyAgentError } from '../services/agentService'

export default function AiAgentsPage() {
  const navigate = useNavigate()
  const { data, isLoading, isError, error, refetch } = useAgentList()

  return (
    <PageContainer>
      <PageHeader
        title="AI Agents"
        description="Every registered PaymentX agent, live from Agent Orchestrator's own registry."
      />
      {isLoading && <LoadingState message="Loading agents…" />}
      {isError && <ErrorState message={classifyAgentError(error)} onRetry={() => refetch()} />}
      {data && data.length === 0 && (
        <EmptyState title="No agents registered" message="Agent Orchestrator's registry currently has no agents." />
      )}
      {data && data.length > 0 && (
        <Grid container spacing={2}>
          {data.map((agent) => (
            <Grid key={agent.agentId} size={{ xs: 12, sm: 6, md: 4 }}>
              <Card variant="outlined" sx={{ height: '100%' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                      <Typography variant="subtitle1" fontWeight={600}>
                        {agent.name}
                      </Typography>
                      <Chip
                        size="small"
                        label={agent.enabled ? 'ENABLED' : 'DISABLED'}
                        color={agent.enabled ? 'success' : 'default'}
                      />
                    </Stack>
                    <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                      {agent.agentId}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {agent.description}
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                      {agent.capabilities.map((capability) => (
                        <Chip key={capability} size="small" variant="outlined" label={capability} />
                      ))}
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Risk level: {agent.riskLevel} · v{agent.version}
                    </Typography>
                    <Button
                      size="small"
                      variant="contained"
                      disabled={!agent.enabled}
                      onClick={() => navigate('/ai-agents/execute', { state: { agentId: agent.agentId } })}
                    >
                      Execute
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}
    </PageContainer>
  )
}
