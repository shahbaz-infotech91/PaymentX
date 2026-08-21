/**
 * ENGLISH: Phase 6 production hardening - a real React error boundary
 * around the routed page content. What it does: if any single page's
 * render throws an unexpected JS error (a coding bug, an unexpected
 * data shape a fetch's own real ErrorState didn't already catch), this
 * boundary catches it and shows a real, recoverable error panel in
 * place of a blank white screen - the Header/Sidebar/Footer around it
 * stay fully interactive, so the user can navigate to a different,
 * working page without a full browser reload. Why it exists: the
 * Phase 6 brief's explicit "do not crash the entire dashboard"
 * requirement - one broken page must never take down the whole app
 * shell. How it will communicate with the backend: N/A - this is
 * pure React rendering-failure handling, unrelated to any HTTP call
 * (those already have their own real ErrorState handling per page).
 *
 * HINGLISH: Phase 6 production hardening - routed page content ke
 * around ek real React error boundary. Ye kya karti hai: agar kisi bhi
 * ek page ka render ek unexpected JS error throw kare (ek coding bug,
 * ek unexpected data shape jise ek fetch ke apne real ErrorState ne
 * pehle se catch nahi kiya), ye boundary ise catch karta hai aur ek
 * blank white screen ki jagah ek real, recoverable error panel dikhata
 * hai - iske around ka Header/Sidebar/Footer poori tarah interactive
 * rehta hai, taaki user bina full browser reload ke ek doosre, working
 * page par navigate kar sake. Ye dashboard me kyu hai: Phase 6 brief
 * ka explicit "poore dashboard ko crash mat karo" requirement - ek
 * broken page kabhi poore app shell ko neeche nahi le jaana chahiye.
 * Backend se kaise connect hogi: N/A - ye pure React rendering-failure
 * handling hai, kisi HTTP call se unrelated (unke paas already apna
 * real ErrorState handling hai, har page par).
 */
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Alert, AlertTitle, Button, Stack, Typography } from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'

interface RouteErrorBoundaryProps {
  children: ReactNode
  /** Changing this key (e.g. the current route path) resets a tripped boundary - navigating away recovers automatically. */
  resetKey: string
}

interface RouteErrorBoundaryState {
  error: Error | null
}

export class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): RouteErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error('RouteErrorBoundary caught a render error:', error, info.componentStack)
  }

  componentDidUpdate(prevProps: RouteErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null })
    }
  }

  render() {
    if (this.state.error) {
      return (
        <Stack spacing={2} sx={{ p: 3 }}>
          <Alert severity="error">
            <AlertTitle>This page hit an unexpected error</AlertTitle>
            <Typography variant="body2" sx={{ mb: 1 }}>
              {this.state.error.message || 'An unexpected rendering error occurred.'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              The rest of the dashboard is unaffected - use the sidebar to go to another page, or reload this one.
            </Typography>
          </Alert>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            sx={{ alignSelf: 'flex-start' }}
            onClick={() => window.location.reload()}
          >
            Reload page
          </Button>
        </Stack>
      )
    }
    return this.props.children
  }
}
