/**
 * ENGLISH: The "/settings" page - genuinely functional in Phase 1.
 * What it does: shows the real, currently-active configuration this
 * frontend build was compiled with (VITE_API_BASE_URL, timeout,
 * environment label - all real import.meta.env values, never
 * fabricated), and the real persisted theme mode with a working
 * toggle wired to the same Redux slice the Header uses. Why it
 * exists: required route "/settings"; unlike the other placeholder
 * pages, dashboard preferences and build-time environment
 * configuration ARE real, available data in Phase 1 - showing them
 * here is honest, not fake. How it will communicate with the backend:
 * N/A - reads only compile-time env vars and client-side Redux state,
 * no backend call.
 *
 * HINGLISH: "/settings" page - Phase 1 me genuinely functional hai.
 * Ye kya karti hai: real, currently-active configuration dikhata hai
 * jiske saath ye frontend build compile hua tha (VITE_API_BASE_URL,
 * timeout, environment label - sab real import.meta.env values,
 * kabhi fabricated nahi), aur real persisted theme mode ek working
 * toggle ke saath jo usi Redux slice se wire hai jise Header use karta
 * hai. Ye dashboard me kyu hai: required route "/settings" hai; baaki
 * placeholder pages ke ulat, dashboard preferences aur build-time
 * environment configuration Phase 1 me REAL, available data hain -
 * unhe yahan dikhana honest hai, fake nahi. Backend se kaise connect
 * hogi: N/A - sirf compile-time env vars aur client-side Redux state
 * padhta hai, koi backend call nahi.
 */
import { Divider, FormControlLabel, Paper, Stack, Switch, Typography } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { useAppDispatch, useAppSelector } from '../app/hooks'
import { toggleMode } from '../features/theme/themeSlice'

export default function SettingsPage() {
  const dispatch = useAppDispatch()
  const mode = useAppSelector((state) => state.theme.mode)

  return (
    <PageContainer>
      <PageHeader title="Settings" description="Dashboard preferences and environment configuration." />

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          Appearance
        </Typography>
        <FormControlLabel
          control={<Switch checked={mode === 'dark'} onChange={() => dispatch(toggleMode())} />}
          label={`Dark mode (${mode})`}
        />
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          Backend connection (build-time configuration)
        </Typography>
        <Stack spacing={1} divider={<Divider flexItem />}>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">API base URL</Typography>
            <Typography variant="body2">{import.meta.env.VITE_API_BASE_URL || '(not set)'}</Typography>
          </Stack>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">Request timeout</Typography>
            <Typography variant="body2">{import.meta.env.VITE_API_TIMEOUT_MS || '(default)'} ms</Typography>
          </Stack>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">Environment</Typography>
            <Typography variant="body2">{import.meta.env.VITE_APP_ENVIRONMENT || 'dev'}</Typography>
          </Stack>
        </Stack>
      </Paper>
    </PageContainer>
  )
}
