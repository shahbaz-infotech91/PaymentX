/**
 * ENGLISH: The application shell every routed page renders inside.
 * What it does: composes Header + Sidebar + a scrollable main content
 * area (React Router's <Outlet/>, wrapped in a real RouteErrorBoundary
 * keyed on the current path - Phase 6, so one page's unexpected render
 * error never takes down this shell) + Footer into one responsive
 * frame, with the main area's left margin/width reacting to the
 * sidebar's collapsed state. Why it exists: the required "Application
 * shell" -
 * defined once so every one of the 18 pages automatically gets the
 * same header/sidebar/footer without repeating layout code. How it
 * will communicate with the backend: N/A directly - it composes
 * Header and Footer, which each independently call the real backend
 * via useHealthCheck.
 *
 * HINGLISH: Application shell jiske andar har routed page render
 * hota hai. Ye kya karti hai: Header + Sidebar + ek scrollable main
 * content area (React Router ka <Outlet/>, ek real RouteErrorBoundary
 * me wrapped current path se keyed - Phase 6, taaki ek page ka
 * unexpected render error is shell ko kabhi neeche na le jaaye) +
 * Footer ko ek responsive frame me compose karta hai, main area ka
 * left margin/width sidebar ke collapsed state ke hisaab se react
 * karta hai. Ye dashboard me kyu hai: required "Application shell" hai
 * - ek hi baar define kiya gaya hai taaki 18 me se har page
 * automatically same header/sidebar/footer paaye, layout code repeat
 * kiye bina. Backend se kaise connect hogi: directly N/A - ye Header
 * aur Footer compose karta hai, jo har ek independently real backend
 * ko useHealthCheck ke through call karta hai.
 */
import { useState } from 'react'
import { Box } from '@mui/material'
import { Outlet, useLocation } from 'react-router-dom'
import { Header, HEADER_HEIGHT } from './Header'
import { Sidebar, SIDEBAR_WIDTH_COLLAPSED, SIDEBAR_WIDTH_EXPANDED } from './Sidebar'
import { Footer } from './Footer'
import { useAppSelector } from '../app/hooks'
import { RouteErrorBoundary } from '../components/RouteErrorBoundary'

export function AppLayout() {
  const collapsed = useAppSelector((state) => state.theme.sidebarCollapsed)
  const sidebarWidth = collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED
  const location = useLocation()
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Header onOpenMobileNav={() => setMobileNavOpen(true)} />
      <Sidebar mobileOpen={mobileNavOpen} onMobileClose={() => setMobileNavOpen(false)} />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          ml: { md: `${sidebarWidth}px` },
          transition: (theme) => theme.transitions.create('margin-left'),
        }}
      >
        <Box sx={{ height: HEADER_HEIGHT }} />
        <Box sx={{ flexGrow: 1, overflowY: 'auto' }}>
          <RouteErrorBoundary resetKey={location.pathname}>
            <Outlet />
          </RouteErrorBoundary>
        </Box>
        <Footer />
      </Box>
    </Box>
  )
}
