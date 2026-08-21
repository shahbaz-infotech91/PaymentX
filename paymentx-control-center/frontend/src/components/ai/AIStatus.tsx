/**
 * ENGLISH: The real AI infrastructure status indicator - "● Ready" /
 * "● AI Service Unavailable" / "● Connecting" from the Phase 3.1 brief,
 * but never faked. What it does: renders useAiHealth()'s real
 * loading/error/data state as a colored dot + label using the same
 * theme.palette.status.* tokens StatusBadge/HealthIndicator already
 * use (never a hardcoded color) - isLoading -> "Connecting…", isError
 * or no health endpoint reachable -> "AI service is not configured
 * yet", data.status === 'NOT_CONFIGURED' -> "Not Configured",
 * data.status === 'NOT_READY' -> "AI Service Unavailable", and only
 * data.status === 'READY' (never actually sent by the real backend in
 * Phase 3.1 - see AiComponentStatus.java) would ever render "Ready".
 * Why it exists: this phase's explicit Step 5 requirement - "Do NOT
 * fake the status. It must come from a real backend health endpoint...
 * clearly show 'AI service is not configured yet'" - satisfied by
 * construction, not by convention. How it will communicate with the
 * backend: via useAiHealth (hooks/useAiHealth.ts) ->
 * services/aiService.ts -> real GET /api/v1/ai/health.
 *
 * HINGLISH: Real AI infrastructure status indicator - Phase 3.1 brief
 * se "● Ready" / "● AI Service Unavailable" / "● Connecting", lekin
 * kabhi fake nahi. Ye kya karti hai: useAiHealth() ka real
 * loading/error/data state ek colored dot + label ke roop me render
 * karta hai, wahi theme.palette.status.* tokens use karte hue jo
 * StatusBadge/HealthIndicator already use karte hain (kabhi hardcoded
 * color nahi) - isLoading -> "Connecting…", isError ya health endpoint
 * reachable na ho -> "AI service is not configured yet",
 * data.status === 'NOT_CONFIGURED' -> "Not Configured", data.status
 * === 'NOT_READY' -> "AI Service Unavailable", aur sirf data.status
 * === 'READY' (Phase 3.1 me real backend kabhi actually nahi bhejta -
 * AiComponentStatus.java dekho) kabhi "Ready" render karega. Ye
 * dashboard me kyu hai: is phase ka explicit Step 5 requirement -
 * "Status fake mat karo. Ye ek real backend health endpoint se aana
 * chahiye... clearly 'AI service is not configured yet' dikhao" -
 * construction se hi satisfy hota hai, convention se nahi. Backend se
 * kaise connect hogi: useAiHealth (hooks/useAiHealth.ts) ke through ->
 * services/aiService.ts -> real GET /api/v1/ai/health.
 */
import { Box, Stack, Tooltip, Typography } from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useAiHealth } from '../../hooks/useAiHealth'

type DisplayState = 'connecting' | 'not-configured' | 'not-ready' | 'ready' | 'unreachable'

export function AIStatus() {
  const theme = useTheme()
  const { data, isLoading, isError } = useAiHealth()

  const state: DisplayState = (() => {
    if (isLoading) return 'connecting'
    if (isError || !data) return 'unreachable'
    if (data.status === 'READY') return 'ready'
    if (data.status === 'NOT_CONFIGURED') return 'not-configured'
    return 'not-ready'
  })()

  const presentation: Record<DisplayState, { label: string; color: string; tooltip: string }> = {
    connecting: { label: 'Connecting…', color: theme.palette.status.unknown, tooltip: 'Checking AI service health…' },
    unreachable: {
      label: 'AI service is not configured yet',
      color: theme.palette.status.unknown,
      tooltip: 'Could not reach the AI health endpoint.',
    },
    'not-configured': {
      label: 'Not Configured',
      color: theme.palette.status.degraded,
      tooltip: 'An operator has not enabled the AI Assistant for this environment yet.',
    },
    'not-ready': {
      label: 'AI Service Unavailable',
      color: theme.palette.status.down,
      tooltip: 'AI Assistant is enabled, but the Prompt/LLM/RAG/MCP/Agent services it depends on are not built yet.',
    },
    ready: { label: 'Ready', color: theme.palette.status.up, tooltip: 'AI Assistant is available.' },
  }

  const { label, color, tooltip } = presentation[state]

  return (
    <Tooltip title={tooltip}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: color, flexShrink: 0 }} />
        <Typography variant="body2" sx={{ color, fontWeight: 600 }}>
          {label}
        </Typography>
      </Stack>
    </Tooltip>
  )
}
