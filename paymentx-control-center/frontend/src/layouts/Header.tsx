/**
 * ENGLISH: The top app bar. What it does: shows the app name, a
 * sidebar-collapse toggle (desktop) and a real mobile nav trigger
 * (xs/sm - Phase 6 hardening, opens Sidebar's overlay Drawer since the
 * permanent one is desktop-only), the live HealthIndicator (real data
 * from useHealthCheck), an environment indicator
 * (VITE_APP_ENVIRONMENT), and the light/dark theme toggle. Why it
 * exists: the required "Top navigation" plus "User/environment
 * indicator" - all cross-page chrome lives here once instead of
 * per-page. How it will communicate with the backend: indirectly, via
 * useHealthCheck's real call to GET /api/v1/health.
 *
 * HINGLISH: Top app bar. Ye kya karti hai: app ka naam, ek sidebar-
 * collapse toggle, live HealthIndicator (useHealthCheck se real data),
 * ek environment indicator (VITE_APP_ENVIRONMENT), aur light/dark
 * theme toggle dikhata hai. Ye dashboard me kyu hai: required "Top
 * navigation" plus "User/environment indicator" hai - saara cross-page
 * chrome yahan ek hi baar rehta hai, har page me nahi. Backend se
 * kaise connect hogi: indirectly, useHealthCheck ki real call ke
 * through GET /api/v1/health tak.
 */
import { AppBar, Chip, IconButton, Stack, Toolbar, Tooltip, Typography } from '@mui/material'
import MenuIcon from '@mui/icons-material/Menu'
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined'
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined'
import { useAppDispatch, useAppSelector } from '../app/hooks'
import { toggleMode, toggleSidebar } from '../features/theme/themeSlice'
import { HealthIndicator } from '../components/HealthIndicator'
import { GlobalSearchBox } from '../components/GlobalSearchBox'
import { AlertsBell } from '../components/AlertsBell'
import { useHealthCheck } from '../hooks/useHealthCheck'
import type { Status } from '../types/common'
import { SIDEBAR_WIDTH_COLLAPSED, SIDEBAR_WIDTH_EXPANDED } from './Sidebar'

export const HEADER_HEIGHT = 64

interface HeaderProps {
  onOpenMobileNav: () => void
}

export function Header({ onOpenMobileNav }: HeaderProps) {
  const dispatch = useAppDispatch()
  const mode = useAppSelector((state) => state.theme.mode)
  const collapsed = useAppSelector((state) => state.theme.sidebarCollapsed)
  const { data, isLoading, isError } = useHealthCheck()

  const backendStatus: Status = isLoading ? 'UNKNOWN' : isError ? 'DOWN' : 'UP'
  const sidebarWidth = collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED

  return (
    <AppBar
      position="fixed"
      color="default"
      sx={{
        width: { md: `calc(100% - ${sidebarWidth}px)` },
        ml: { md: `${sidebarWidth}px` },
        bgcolor: 'background.paper',
        transition: (theme) => theme.transitions.create(['width', 'margin']),
      }}
    >
      <Toolbar sx={{ height: HEADER_HEIGHT, gap: 2 }}>
        <IconButton
          edge="start"
          onClick={() => dispatch(toggleSidebar())}
          sx={{ display: { xs: 'none', md: 'inline-flex' } }}
          aria-label="Toggle sidebar"
        >
          <MenuIcon />
        </IconButton>
        <IconButton
          edge="start"
          onClick={onOpenMobileNav}
          sx={{ display: { xs: 'inline-flex', md: 'none' } }}
          aria-label="Open navigation menu"
        >
          <MenuIcon />
        </IconButton>

        <Typography variant="h6" noWrap sx={{ fontWeight: 700, flexShrink: 0 }}>
          PaymentX Control Center
        </Typography>

        <Stack sx={{ display: { xs: 'none', sm: 'flex' }, ml: 2 }}>
          <GlobalSearchBox />
        </Stack>

        <Stack direction="row" spacing={2} alignItems="center" sx={{ ml: 'auto' }}>
          <Chip size="small" label={import.meta.env.VITE_APP_ENVIRONMENT || 'dev'} variant="outlined" />
          <HealthIndicator status={backendStatus} label={data ? `Backend ${data.status}` : undefined} />
          <AlertsBell />
          <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
            <IconButton onClick={() => dispatch(toggleMode())} aria-label="Toggle color mode">
              {mode === 'dark' ? <LightModeOutlinedIcon /> : <DarkModeOutlinedIcon />}
            </IconButton>
          </Tooltip>
        </Stack>
      </Toolbar>
    </AppBar>
  )
}
