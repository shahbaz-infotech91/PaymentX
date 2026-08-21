/**
 * ENGLISH: The single configured Axios instance every API call in this
 * app goes through. What it does: sets baseURL from VITE_API_BASE_URL
 * (defaulting to the backend's real dev port, 8089) and a request
 * timeout from VITE_API_TIMEOUT_MS, and normalizes error responses
 * into the ApiErrorResponse shape the backend's GlobalExceptionHandler
 * actually returns. Why it exists: one place to configure the
 * frontend-to-backend HTTP boundary instead of every service file
 * repeating baseURL/timeout/error-shape logic. How it will communicate
 * with the backend: this literally IS the frontend-to-backend
 * connection - every function in src/services/ calls this client, and
 * this client's baseURL is what CorsConfig.java on the backend must
 * (and does) allow.
 *
 * HINGLISH: Ek hi configured Axios instance jisse is app ki har API
 * call guzarti hai. Ye kya karti hai: VITE_API_BASE_URL se baseURL set
 * karta hai (default backend ke real dev port, 8089, par), aur
 * VITE_API_TIMEOUT_MS se ek request timeout, aur error responses ko
 * ApiErrorResponse shape me normalize karta hai jo backend ka
 * GlobalExceptionHandler actually return karta hai. Ye dashboard me
 * kyu hai: frontend-se-backend HTTP boundary configure karne ke liye
 * ek hi jagah, har service file me baseURL/timeout/error-shape logic
 * repeat karne ke bajaye. Backend se kaise connect hogi: ye literally
 * frontend-se-backend connection HAI - src/services/ ka har function
 * is client ko call karta hai, aur is client ka baseURL wahi hai jise
 * backend ka CorsConfig.java allow karta hai (aur karta hai).
 */
import axios, { AxiosError } from 'axios'
import type { ApiErrorResponse } from '../types/common'
import { getDashboardToken } from '../utils/dashboardAuth'

export const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8089',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT_MS) || 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ENGLISH: Attaches the real Phase 6 dashboard access token, when one has been entered (see AuthGate.tsx) - a
// harmless no-op header when the real backend's control-center.security.enabled is false (the default).
// HINGLISH: Real Phase 6 dashboard access token attach karta hai, jab ek enter kiya gaya ho (AuthGate.tsx dekho) -
// ek harmless no-op header jab real backend ka control-center.security.enabled false ho (default).
axiosClient.interceptors.request.use((config) => {
  const token = getDashboardToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/** Normalizes any Axios failure (network error, timeout, 4xx/5xx) into one shape callers can rely on. */
export function toApiError(error: unknown): ApiErrorResponse {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ error?: ApiErrorResponse }>
    const backendError = axiosError.response?.data?.error
    if (backendError) {
      return backendError
    }
    if (axiosError.code === 'ECONNABORTED') {
      return { errorCode: 'TIMEOUT', message: 'The request timed out.', path: axiosError.config?.url ?? '' }
    }
    if (!axiosError.response) {
      return { errorCode: 'NETWORK_ERROR', message: 'Could not reach the Control Center backend.', path: axiosError.config?.url ?? '' }
    }
    return { errorCode: 'UNKNOWN_ERROR', message: axiosError.message, path: axiosError.config?.url ?? '' }
  }
  return { errorCode: 'UNKNOWN_ERROR', message: 'An unexpected error occurred.', path: '' }
}
