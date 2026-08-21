/**
 * ENGLISH: The bridge between Redux's theme state and MUI's theme
 * system. What it does: reads the real, persisted mode from the
 * theme Redux slice, builds the corresponding MUI theme via
 * theme/theme.ts, and applies it via ThemeProvider + CssBaseline (the
 * standard MUI reset). Why it exists: has to be a separate component
 * from App.tsx because it calls useAppSelector, which requires being
 * inside <ReduxProvider> - App.tsx itself renders the ReduxProvider,
 * so this logic can't live in App.tsx directly. Why it exists (Phase
 * 1 theme requirement): this is literally what makes "Persist selected
 * theme" and "Support dark mode / light mode" real - the selected
 * mode survives a reload because themeSlice.ts reads localStorage on
 * init and this component re-renders whenever that Redux state
 * changes. How it will communicate with the backend: N/A - pure
 * client-side theming.
 *
 * HINGLISH: Redux ke theme state aur MUI ke theme system ke beech ka
 * bridge. Ye kya karti hai: theme Redux slice se real, persisted mode
 * padhta hai, theme/theme.ts ke through corresponding MUI theme
 * banata hai, aur ThemeProvider + CssBaseline (standard MUI reset) ke
 * through apply karta hai. Ye dashboard me kyu hai: App.tsx se ek alag
 * component hona zaroori hai kyunki ye useAppSelector call karta hai,
 * jise <ReduxProvider> ke andar hona zaroori hai - App.tsx khud
 * ReduxProvider render karta hai, isliye ye logic App.tsx me directly
 * nahi reh sakta. Ye dashboard me kyu hai (Phase 1 theme requirement):
 * ye literally wahi cheez hai jo "Persist selected theme" aur "Support
 * dark mode / light mode" ko real banati hai - selected mode reload ke
 * baad bhi bacha rehta hai kyunki themeSlice.ts init par localStorage
 * padhta hai aur ye component har baar re-render hota hai jab bhi wo
 * Redux state change hoti hai. Backend se kaise connect hogi: N/A -
 * pure client-side theming hai.
 */
import { useMemo, type ReactNode } from 'react'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { useAppSelector } from './hooks'
import { buildTheme } from '../theme/theme'

interface ThemedAppProps {
  children: ReactNode
}

export function ThemedApp({ children }: ThemedAppProps) {
  const mode = useAppSelector((state) => state.theme.mode)
  const theme = useMemo(() => buildTheme(mode), [mode])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  )
}
