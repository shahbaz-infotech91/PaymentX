/**
 * ENGLISH: The actual JavaScript entry point Vite bundles from
 * index.html. What it does: mounts <App/> into the #root DOM node
 * using React 19's createRoot API, wrapped in StrictMode to surface
 * real bugs (double-invoked effects, unsafe lifecycle patterns) during
 * development. Why it exists: every Vite React app needs exactly one
 * of these - this is where the React tree actually starts existing.
 * How it will communicate with the backend: N/A directly - it renders
 * App, which is what eventually calls the backend.
 *
 * HINGLISH: Actual JavaScript entry point jise Vite index.html se
 * bundle karta hai. Ye kya karti hai: React 19 ke createRoot API se
 * <App/> ko #root DOM node me mount karta hai, StrictMode me wrapped,
 * taaki development ke dauraan real bugs (double-invoked effects,
 * unsafe lifecycle patterns) samne aayein. Ye dashboard me kyu hai:
 * har Vite React app ko exactly ek aisa chahiye - yahin se React tree
 * actually exist karna shuru hota hai. Backend se kaise connect hogi:
 * directly N/A - ye App render karta hai, jo eventually backend ko
 * call karta hai.
 */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Root element "#root" not found in index.html')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
