/**
 * ENGLISH: The root React component. What it does: wraps the whole
 * app in every cross-cutting provider it needs, in the correct
 * nesting order - Redux (theme state) -> ThemedApp (reads that state
 * and applies MUI's ThemeProvider + CssBaseline) -> React Query ->
 * AuthGate (Phase 6 - blocks on a real 401 from the backend's optional
 * dashboard-token auth, a no-op when it's disabled) -> the actual
 * router. Why it exists: one place where "what wraps the app, and in
 * what order" is decided, instead of scattering providers across
 * main.tsx and individual components. How it will communicate with the
 * backend: N/A directly - it provides the QueryClientProvider every
 * page's backend-calling hooks depend on.
 *
 * HINGLISH: Root React component. Ye kya karti hai: poori app ko har
 * cross-cutting provider me wrap karta hai jiski use zaroorat hai,
 * sahi nesting order me - Redux (theme state) -> ThemedApp (wo state
 * padhta hai aur MUI ka ThemeProvider + CssBaseline apply karta hai)
 * -> React Query -> AuthGate (Phase 6 - backend ke optional
 * dashboard-token auth se ek real 401 par block karta hai, disabled
 * hone par no-op) -> actual router. Ye dashboard me kyu hai: ek hi
 * jagah jahan "app ko kya wrap karta hai, aur kis order me" decide
 * hota hai, main.tsx aur individual components me providers bikharne
 * ke bajaye. Backend se kaise connect hogi: directly N/A - ye
 * QueryClientProvider provide karta hai jis par har page ke
 * backend-calling hooks depend karte hain.
 */
import { Provider as ReduxProvider } from 'react-redux'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { store } from './store'
import { queryClient } from '../api/queryClient'
import { router } from './router'
import { ThemedApp } from './ThemedApp'
import { AuthGate } from './AuthGate'

export function App() {
  return (
    <ReduxProvider store={store}>
      <ThemedApp>
        <QueryClientProvider client={queryClient}>
          <AuthGate>
            <RouterProvider router={router} />
          </AuthGate>
        </QueryClientProvider>
      </ThemedApp>
    </ReduxProvider>
  )
}
