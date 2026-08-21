/**
 * ENGLISH: Reads/writes the real Phase 6 dashboard access token from
 * sessionStorage (never localStorage - a session-scoped credential
 * that a shared/kiosk browser session doesn't silently keep forever).
 * What it does: this is the ONE place the token is stored - never
 * committed to source, never baked into a build, only ever typed in by
 * an operator at runtime when control-center.security.enabled=true on
 * the real backend (see backend config/DashboardAuthFilter.java). Why
 * it exists: axiosClient.ts and AuthGate.tsx both need to read/write
 * this exact same value. How it will communicate with the backend:
 * axiosClient.ts attaches it as a real Authorization: Bearer header on
 * every request.
 *
 * HINGLISH: Real Phase 6 dashboard access token ko sessionStorage se
 * padhta/likhta hai (localStorage kabhi nahi - ek session-scoped
 * credential jise ek shared/kiosk browser session silently hamesha ke
 * liye rakh na le). Ye kya karti hai: yehi ek jagah hai jahan token
 * store hota hai - kabhi source me commit nahi hota, kabhi build me
 * bake nahi hota, sirf runtime par ek operator dwara type kiya jaata
 * hai jab real backend par control-center.security.enabled=true ho
 * (backend config/DashboardAuthFilter.java dekho). Ye dashboard me kyu
 * hai: axiosClient.ts aur AuthGate.tsx dono ko yehi exact value
 * padhni/likhni hai. Backend se kaise connect hogi: axiosClient.ts ise
 * har request par ek real Authorization: Bearer header ke roop me
 * attach karta hai.
 */
const STORAGE_KEY = 'paymentx-control-center.dashboard-token'

export function getDashboardToken(): string | null {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function setDashboardToken(token: string): void {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, token)
  } catch {
    // sessionStorage unavailable (e.g. private browsing edge cases) - the token simply won't persist across reloads.
  }
}

export function clearDashboardToken(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
}
