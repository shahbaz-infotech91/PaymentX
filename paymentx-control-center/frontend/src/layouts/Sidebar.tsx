/**
 * ENGLISH: The primary navigation. What it does: renders every route
 * from utils/routes.ts grouped by section (Overview, Payments,
 * Infrastructure, Operations, Tools), highlights the active route via
 * React Router's useLocation, and collapses to icon-only via the theme
 * Redux slice's sidebarCollapsed state - as a permanent Drawer on
 * desktop (md+). Below that width, the permanent Drawer would hide
 * navigation entirely (a real Phase 6 hardening finding: the
 * collapse-toggle button in Header is itself desktop-only, so there
 * was previously no way to reach the nav on a narrow viewport at all)
 * - a second, temporary/overlay Drawer (always full-width, ignoring
 * the desktop collapse state since there's no space to save on mobile)
 * renders the same real ROUTES list, opened by Header's mobile menu
 * button and closed on backdrop click or route selection. Why it
 * exists: the required "Responsive sidebar" - built once here rather
 * than duplicated per page, and driven entirely by ROUTES so it can
 * never drift from the router's actual route list. How it will
 * communicate with the backend: N/A - pure client-side navigation.
 *
 * HINGLISH: Primary navigation. Ye kya karti hai: utils/routes.ts ka
 * har route section ke hisaab se group karke (Overview, Payments,
 * Infrastructure, Operations, Tools) render karta hai, React Router ke
 * useLocation se active route highlight karta hai, aur theme Redux
 * slice ke sidebarCollapsed state se icon-only me collapse hota hai -
 * desktop (md+) par ek permanent Drawer ke roop me. Us width se
 * neeche, permanent Drawer navigation ko poori tarah chhupa deta
 * (ek real Phase 6 hardening finding: Header ka collapse-toggle button
 * khud desktop-only hai, isliye pehle ek narrow viewport par nav tak
 * pahunchne ka koi tareeka hi nahi tha) - ek doosra, temporary/overlay
 * Drawer (hamesha full-width, desktop collapse state ignore karte hue
 * kyunki mobile par bachane layak space nahi hai) wahi real ROUTES
 * list render karta hai, Header ke mobile menu button se open hota
 * hai aur backdrop click ya route selection par band hota hai. Ye
 * dashboard me kyu hai: required "Responsive sidebar" hai - yahan ek
 * hi baar banaya gaya hai har page me duplicate karne ke bajaye, aur
 * poori tarah ROUTES se driven hai taaki ye router ki actual route
 * list se kabhi drift na ho. Backend se kaise connect hogi: N/A - pure
 * client-side navigation hai.
 */
import {
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Typography,
} from '@mui/material'
import { useLocation, useNavigate } from 'react-router-dom'
import { ROUTES, ROUTE_GROUPS } from '../utils/routes'
import { useAppSelector } from '../app/hooks'

export const SIDEBAR_WIDTH_EXPANDED = 248
export const SIDEBAR_WIDTH_COLLAPSED = 72

interface SidebarProps {
  mobileOpen: boolean
  onMobileClose: () => void
}

export function Sidebar({ mobileOpen, onMobileClose }: SidebarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const collapsed = useAppSelector((state) => state.theme.sidebarCollapsed)
  const width = collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED

  function navItems(effectiveCollapsed: boolean, onNavigate: (path: string) => void) {
    return ROUTE_GROUPS.map((group) => (
      <Box key={group} sx={{ mb: 1 }}>
        {!effectiveCollapsed && (
          <Typography
            variant="overline"
            color="text.secondary"
            sx={{ px: 2, pt: 1.5, display: 'block', fontSize: '0.7rem', letterSpacing: 1 }}
          >
            {group}
          </Typography>
        )}
        <List dense disablePadding>
          {ROUTES.filter((route) => route.group === group).map((route) => {
            const Icon = route.icon
            const selected = location.pathname === route.path
            const item = (
              <ListItemButton
                key={route.path}
                selected={selected}
                onClick={() => onNavigate(route.path)}
                sx={{
                  mx: 1,
                  borderRadius: 1.5,
                  justifyContent: effectiveCollapsed ? 'center' : 'flex-start',
                }}
              >
                <ListItemIcon sx={{ minWidth: effectiveCollapsed ? 0 : 40, justifyContent: 'center' }}>
                  <Icon fontSize="small" />
                </ListItemIcon>
                {!effectiveCollapsed && <ListItemText primary={route.label} />}
              </ListItemButton>
            )
            return effectiveCollapsed ? (
              <Tooltip key={route.path} title={route.label} placement="right">
                {item}
              </Tooltip>
            ) : (
              item
            )
          })}
        </List>
      </Box>
    ))
  }

  return (
    <>
      <Drawer
        variant="permanent"
        sx={{
          width,
          flexShrink: 0,
          display: { xs: 'none', md: 'block' },
          [`& .MuiDrawer-paper`]: {
            width,
            boxSizing: 'border-box',
            borderRight: '1px solid',
            borderColor: 'divider',
            transition: (theme) => theme.transitions.create('width'),
            overflowX: 'hidden',
          },
        }}
      >
        <Box sx={{ height: 64 }} />
        {navItems(collapsed, (path) => navigate(path))}
      </Drawer>

      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onMobileClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', md: 'none' },
          [`& .MuiDrawer-paper`]: { width: SIDEBAR_WIDTH_EXPANDED, boxSizing: 'border-box' },
        }}
      >
        <Box sx={{ height: 64 }} />
        {navItems(false, (path) => {
          navigate(path)
          onMobileClose()
        })}
      </Drawer>
    </>
  )
}
