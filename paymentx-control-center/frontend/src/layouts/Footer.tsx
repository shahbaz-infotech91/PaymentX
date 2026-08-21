/**
 * ENGLISH: The persistent bottom status bar. What it does: shows the
 * real backend connection status (via useHealthCheck) with an
 * ErrorState-driven message when the backend is unreachable, and the
 * backend's real reported version. Why it exists: the required
 * "Footer/status area" - a small always-visible strip distinct from
 * the Header's health dot, giving more detail (backend version,
 * profile) without cluttering the top bar. How it will communicate
 * with the backend: indirectly, via the same useHealthCheck hook as
 * the Header - one real call, two consumers.
 *
 * HINGLISH: Persistent bottom status bar. Ye kya karti hai: real
 * backend connection status dikhata hai (useHealthCheck se), jab
 * backend unreachable ho tab ek ErrorState-driven message ke saath,
 * aur backend ka real reported version. Ye dashboard me kyu hai:
 * required "Footer/status area" hai - ek chhoti always-visible strip,
 * Header ke health dot se alag, zyada detail deti hai (backend
 * version, profile) bina top bar ko clutter kiye. Backend se kaise
 * connect hogi: indirectly, Header jaise hi useHealthCheck hook ke
 * through - ek real call, do consumers.
 */
import { Box, Stack, Typography } from '@mui/material'
import { useHealthCheck } from '../hooks/useHealthCheck'
import { formatTimestamp } from '../utils/formatters'

export const FOOTER_HEIGHT = 36

export function Footer() {
  const { data, isError, error } = useHealthCheck()

  return (
    <Box
      component="footer"
      sx={{
        height: FOOTER_HEIGHT,
        px: 2,
        display: 'flex',
        alignItems: 'center',
        borderTop: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
      }}
    >
      <Stack direction="row" spacing={2} sx={{ width: '100%' }} alignItems="center">
        {isError ? (
          <Typography variant="caption" color="error">
            Cannot reach Control Center backend: {error instanceof Error ? error.message : 'unknown error'}
          </Typography>
        ) : data ? (
          <Typography variant="caption" color="text.secondary">
            {data.applicationName} · profile {data.activeProfile} · v{data.version} · last check {formatTimestamp(data.serverTime)}
          </Typography>
        ) : (
          <Typography variant="caption" color="text.secondary">
            Connecting to backend…
          </Typography>
        )}
      </Stack>
    </Box>
  )
}
