/**
 * ENGLISH: The Redux Toolkit slice owning the app's light/dark theme
 * mode and sidebar-collapsed state. What it does: reads the initial
 * mode from localStorage (falling back to the OS preference via
 * prefers-color-scheme), and persists every change back to
 * localStorage so a reload doesn't flash the wrong theme. Why it
 * exists: this is deliberately the ONLY Redux slice in Phase 1 -
 * "Redux Toolkit only where actually required" - theme mode is real
 * cross-cutting state read by the AppLayout, every page, and MUI's
 * ThemeProvider simultaneously, which is exactly the kind of global
 * state Redux is for; most other state in this app (server data) goes
 * through TanStack Query instead, not Redux. How it will communicate
 * with the backend: N/A - purely client-side UI preference, never
 * sent to the backend.
 *
 * HINGLISH: Redux Toolkit slice jo app ka light/dark theme mode aur
 * sidebar-collapsed state rakhta hai. Ye kya karti hai: localStorage
 * se initial mode padhta hai (fallback OS preference par
 * prefers-color-scheme ke through), aur har change ko localStorage me
 * wapas persist karta hai taaki reload par galat theme flash na ho.
 * Ye dashboard me kyu hai: Phase 1 me jaan-bujhkar ye EK-hi Redux
 * slice hai - "Redux Toolkit only where actually required" - theme
 * mode real cross-cutting state hai jise AppLayout, har page, aur
 * MUI ka ThemeProvider ek saath padhte hain, exactly waisi global
 * state jiske liye Redux bana hai; is app ki baaki state (server data)
 * Redux ke bajaye TanStack Query se hoti hai. Backend se kaise connect
 * hogi: N/A - purely client-side UI preference hai, backend ko kabhi
 * nahi bheja jata.
 */
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { ThemeMode } from '../../theme/theme'

const STORAGE_KEY = 'paymentx-control-center:theme-mode'
const SIDEBAR_STORAGE_KEY = 'paymentx-control-center:sidebar-collapsed'

function readStoredMode(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark') {
    return stored
  }
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
  return prefersDark ? 'dark' : 'light'
}

function readStoredSidebarCollapsed(): boolean {
  return localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true'
}

export interface ThemeState {
  mode: ThemeMode
  sidebarCollapsed: boolean
}

const initialState: ThemeState = {
  mode: readStoredMode(),
  sidebarCollapsed: readStoredSidebarCollapsed(),
}

const themeSlice = createSlice({
  name: 'theme',
  initialState,
  reducers: {
    toggleMode(state) {
      state.mode = state.mode === 'light' ? 'dark' : 'light'
      localStorage.setItem(STORAGE_KEY, state.mode)
    },
    setMode(state, action: PayloadAction<ThemeMode>) {
      state.mode = action.payload
      localStorage.setItem(STORAGE_KEY, state.mode)
    },
    toggleSidebar(state) {
      state.sidebarCollapsed = !state.sidebarCollapsed
      localStorage.setItem(SIDEBAR_STORAGE_KEY, String(state.sidebarCollapsed))
    },
  },
})

export const { toggleMode, setMode, toggleSidebar } = themeSlice.actions
export default themeSlice.reducer
