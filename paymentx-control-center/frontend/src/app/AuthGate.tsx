/**
 * ENGLISH: The frontend half of the Phase 6 dashboard authentication
 * gate (backend half: config/DashboardAuthFilter.java). What it does:
 * probes the real GET /api/v1/health endpoint once on mount using
 * whatever token (if any) is already in sessionStorage; a real 401
 * means control-center.security.enabled=true on the real backend and
 * this browser doesn't have a valid token yet, so it shows a token
 * entry form instead of the app. Any OTHER outcome (200, network
 * error, backend genuinely unreachable) renders the real app
 * immediately - this gate only ever blocks on a real, confirmed 401,
 * never on "the backend happens to be down" (that's each page's own
 * real ErrorState's job, not this gate's). When
 * control-center.security.enabled is false (this repo's default),
 * every prior phase's verified zero-friction local-dev flow is
 * unchanged - the health probe returns 200 immediately and the app
 * renders with no visible gate at all.
 *
 * HINGLISH: Phase 6 dashboard authentication gate ka frontend half
 * (backend half: config/DashboardAuthFilter.java). Ye kya karti hai:
 * mount par ek baar real GET /api/v1/health endpoint ko probe karta
 * hai, jo bhi token (agar ho) sessionStorage me already hai use karke;
 * ek real 401 ka matlab hai real backend par
 * control-center.security.enabled=true hai aur is browser ke paas
 * abhi valid token nahi hai, isliye ye app ke bajaye ek token entry
 * form dikhata hai. Koi bhi DOOSRA outcome (200, network error,
 * backend genuinely unreachable) turant real app render karta hai - ye
 * gate sirf ek real, confirmed 401 par hi block karta hai, kabhi "backend
 * abhi down hai" par nahi (wo har page ke apne real ErrorState ka kaam
 * hai, is gate ka nahi). Jab control-center.security.enabled false ho
 * (is repo ka default), har pichle phase ka verified zero-friction
 * local-dev flow unchanged rehta hai - health probe turant 200 return
 * karta hai aur app bina kisi visible gate ke render hota hai.
 */
import { useEffect, useState, type ReactNode } from 'react'
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { fetchDashboardHealth } from '../services/healthService'
import { setDashboardToken } from '../utils/dashboardAuth'
import { LoadingState } from '../components/LoadingState'

type GateState = 'checking' | 'open' | 'locked'

export function AuthGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<GateState>('checking')
  const [tokenInput, setTokenInput] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  async function probe() {
    try {
      await fetchDashboardHealth()
      setState('open')
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setState(status === 401 ? 'locked' : 'open')
    }
  }

  useEffect(() => {
    probe()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleUnlock() {
    setSubmitError(null)
    setDashboardToken(tokenInput.trim())
    setState('checking')
    try {
      await fetchDashboardHealth()
      setState('open')
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 401) {
        setSubmitError('That token was rejected by the real backend. Check it and try again.')
        setState('locked')
      } else {
        setState('open')
      }
    }
  }

  if (state === 'checking') {
    return <LoadingState message="Connecting to Control Center backend…" />
  }

  if (state === 'locked') {
    return (
      <Box sx={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center', p: 2 }}>
        <Paper variant="outlined" sx={{ p: 4, maxWidth: 420, width: '100%' }}>
          <Stack spacing={2} alignItems="center">
            <LockOutlinedIcon fontSize="large" color="action" />
            <Typography variant="h6">Dashboard Access Required</Typography>
            <Typography variant="body2" color="text.secondary" textAlign="center">
              This Control Center backend has authentication enabled. Enter the real dashboard access token to
              continue.
            </Typography>
            {submitError && <Alert severity="error">{submitError}</Alert>}
            <TextField
              label="Dashboard access token"
              type="password"
              value={tokenInput}
              onChange={(e) => setTokenInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleUnlock()
              }}
              fullWidth
              size="small"
              autoFocus
            />
            <Button variant="contained" fullWidth disabled={!tokenInput.trim()} onClick={handleUnlock}>
              Unlock
            </Button>
          </Stack>
        </Paper>
      </Box>
    )
  }

  return <>{children}</>
}
