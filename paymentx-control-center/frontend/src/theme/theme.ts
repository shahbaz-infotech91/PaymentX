/**
 * ENGLISH: Builds the Material UI theme object for either light or dark
 * mode. What it does: defines semantic color tokens (status.up/down/
 * degraded, surface levels) on top of MUI's palette so components never
 * hardcode raw hex colors - they reference theme.palette.status.up etc.
 * Why it exists: "do not use random colors everywhere... create
 * reusable semantic theme tokens" is an explicit Phase 1 requirement;
 * centralizing this here means a StatusBadge, HealthIndicator, and
 * MetricCard all agree on what color "healthy" means without each
 * hardcoding its own #4caf50. How it will communicate with the
 * backend: N/A - pure presentation, no backend calls.
 *
 * HINGLISH: Light ya dark mode ke liye Material UI theme object
 * banata hai. Ye kya karti hai: MUI ke palette ke upar semantic color
 * tokens (status.up/down/degraded, surface levels) define karta hai
 * taaki components kabhi raw hex colors hardcode na karein - wo
 * theme.palette.status.up etc. reference karte hain. Ye dashboard me
 * kyu hai: "do not use random colors everywhere... create reusable
 * semantic theme tokens" ek explicit Phase 1 requirement hai; ise
 * yahan centralize karne ka matlab hai ki StatusBadge, HealthIndicator,
 * aur MetricCard sab is baat par agree karte hain ki "healthy" ka
 * color kya hai, bina apna khud ka #4caf50 hardcode kiye. Backend se
 * kaise connect hogi: N/A - pure presentation hai, koi backend calls
 * nahi.
 */
import { createTheme, type ThemeOptions } from '@mui/material/styles'

// Augments MUI's Palette so `theme.palette.status.up` etc. type-checks.
declare module '@mui/material/styles' {
  interface Palette {
    status: {
      up: string
      down: string
      degraded: string
      unknown: string
    }
  }
  interface PaletteOptions {
    status?: {
      up: string
      down: string
      degraded: string
      unknown: string
    }
  }
}

export type ThemeMode = 'light' | 'dark'

const statusTokens = {
  light: { up: '#1B8A3F', down: '#C62828', degraded: '#B8860B', unknown: '#78909C' },
  dark: { up: '#4CAF6F', down: '#EF5350', degraded: '#F0B429', unknown: '#90A4AE' },
}

function buildThemeOptions(mode: ThemeMode): ThemeOptions {
  const isDark = mode === 'dark'
  return {
    palette: {
      mode,
      primary: { main: isDark ? '#5B8DEF' : '#1565C0' },
      secondary: { main: isDark ? '#B39DDB' : '#5E35B1' },
      background: {
        default: isDark ? '#0F1115' : '#F5F7FA',
        paper: isDark ? '#171A21' : '#FFFFFF',
      },
      status: statusTokens[mode],
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: [
        'Inter',
        '-apple-system',
        'Segoe UI',
        'Roboto',
        'Helvetica',
        'Arial',
        'sans-serif',
      ].join(','),
      h1: { fontSize: '1.75rem', fontWeight: 600 },
      h2: { fontSize: '1.375rem', fontWeight: 600 },
      h6: { fontWeight: 600 },
    },
    components: {
      MuiAppBar: {
        styleOverrides: {
          root: { boxShadow: 'none', borderBottom: '1px solid', borderColor: isDark ? '#262B36' : '#E3E8EF' },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: { backgroundImage: 'none' },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: { border: '1px solid', borderColor: isDark ? '#262B36' : '#E3E8EF' },
        },
      },
    },
  }
}

export function buildTheme(mode: ThemeMode) {
  return createTheme(buildThemeOptions(mode))
}
