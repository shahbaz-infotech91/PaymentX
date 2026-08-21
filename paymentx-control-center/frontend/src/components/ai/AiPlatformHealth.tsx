/**
 * ENGLISH: Phase 3.10.3 - "Overall Health" section of the AI Metrics page. What it does: renders the
 * real, per-component health breakdown useAiHealth() already fetches (components: Record<string,
 * AiComponentStatus>) but that, before this phase, only ever surfaced as one rolled-up dot (AIStatus.tsx,
 * in the header). No second health implementation is created here - this component consumes the exact
 * same hook/endpoint AIStatus.tsx already uses (GET /api/v1/ai/health via useAiHealth()), just renders
 * more of what it already returns. Each of the 7 real components (chatInterface/promptService/
 * llmService/embeddingService/ragService/mcpGateway/agentOrchestrator - see backend AiChatService.health()'s
 * javadoc) reuses the existing HealthIndicator component and theme.palette.status.* tokens
 * AIStatus.tsx/HealthIndicator already establish - READY maps to UP, NOT_READY to DOWN,
 * NOT_IMPLEMENTED to UNKNOWN, matching AiComponentStatus's own real backend semantics (see
 * types/ai.ts). Loading/error states mirror AIStatus.tsx's own honest, never-fabricated approach.
 *
 * HINGLISH: Phase 3.10.3 - AI Metrics page ka "Overall Health" section. Ye kya karti hai: real,
 * per-component health breakdown render karta hai jo useAiHealth() already fetch karta hai
 * (components: Record<string, AiComponentStatus>), lekin jo is phase se pehle sirf ek rolled-up dot ke
 * roop me hi surface hota tha (AIStatus.tsx, header me). Yahan koi doosra health implementation nahi
 * banaya gaya - ye component wahi hook/endpoint use karta hai jo AIStatus.tsx already use karta hai (GET
 * /api/v1/ai/health, useAiHealth() ke through), bas jo wo already return karta hai uska zyada hissa
 * render karta hai. 7 real components me se har ek (chatInterface/promptService/llmService/
 * embeddingService/ragService/mcpGateway/agentOrchestrator) existing HealthIndicator component aur
 * theme.palette.status.* tokens reuse karta hai jo AIStatus.tsx/HealthIndicator already establish karte
 * hain.
 */
import { Card, CardContent, Grid2 as Grid, Typography } from '@mui/material'
import { HealthIndicator } from '../HealthIndicator'
import { LoadingState } from '../LoadingState'
import { ErrorState } from '../ErrorState'
import { useAiHealth } from '../../hooks/useAiHealth'
import { toApiError } from '../../api/axiosClient'
import type { AiComponentStatus } from '../../types/ai'
import type { Status } from '../../types/common'

const COMPONENT_LABELS: Record<string, string> = {
  chatInterface: 'Chat Interface',
  promptService: 'Prompt',
  llmService: 'LLM',
  embeddingService: 'Embedding',
  ragService: 'RAG',
  mcpGateway: 'MCP Gateway',
  agentOrchestrator: 'Agent Orchestrator',
}

/** AiComponentStatus (backend dto/ai/AiComponentStatus.java) has no DEGRADED value - only these three -
 * so this mapping is total and never falls through to a fabricated default. */
function toHealthIndicatorStatus(status: AiComponentStatus): Status {
  switch (status) {
    case 'READY':
      return 'UP'
    case 'NOT_READY':
      return 'DOWN'
    case 'NOT_IMPLEMENTED':
    default:
      return 'UNKNOWN'
  }
}

export function AiPlatformHealth() {
  const { data, isLoading, isError, error, refetch } = useAiHealth()

  if (isLoading) return <LoadingState message="Checking AI Platform health…" />
  if (isError) return <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />
  if (!data) return <ErrorState message="AI health endpoint returned no data." onRetry={() => refetch()} />

  const componentEntries = Object.entries(data.components)

  return (
    <Grid container spacing={2}>
      {componentEntries.map(([key, status]) => (
        <Grid key={key} size={{ xs: 6, sm: 4, md: 3 }}>
          <Card variant="outlined">
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                {COMPONENT_LABELS[key] ?? key}
              </Typography>
              <HealthIndicator status={toHealthIndicatorStatus(status)} />
            </CardContent>
          </Card>
        </Grid>
      ))}
      {componentEntries.length === 0 && (
        <Grid size={12}>
          <Typography variant="body2" color="text.secondary">
            No AI component health data is available yet.
          </Typography>
        </Grid>
      )}
    </Grid>
  )
}
