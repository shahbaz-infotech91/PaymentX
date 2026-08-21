/**
 * ENGLISH: The one real backend-calling service in Phase 1. What it
 * does: calls GET /api/v1/health on the Control Center backend and
 * unwraps the ApiResponse<HealthResponse> envelope. Why it exists:
 * every other feature (Kafka/Redis/Payments/etc. pages) is genuinely
 * empty-state in Phase 1 because there is nothing real to call yet -
 * this service exists to prove the frontend-to-backend wiring
 * (Vite dev server -> CORS -> Spring controller -> JSON back) actually
 * works end to end with one real, live call, not a mock. How it will
 * communicate with the backend: this literally is the HTTP call to
 * HealthController on the backend.
 *
 * HINGLISH: Phase 1 me ye ek hi real backend-calling service hai. Ye
 * kya karti hai: Control Center backend par GET /api/v1/health call
 * karta hai aur ApiResponse<HealthResponse> envelope ko unwrap karta
 * hai. Ye dashboard me kyu hai: baaki har feature (Kafka/Redis/
 * Payments/etc. pages) Phase 1 me genuinely empty-state hai kyunki
 * abhi call karne layak kuch real nahi hai - ye service isliye exist
 * karti hai taaki frontend-se-backend wiring (Vite dev server -> CORS
 * -> Spring controller -> JSON wapas) end-to-end actually kaam kare,
 * ek real, live call ke saath, mock nahi. Backend se kaise connect
 * hogi: ye literally backend par HealthController ko HTTP call hai.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface DashboardHealth {
  status: string
  applicationName: string
  activeProfile: string
  version: string
  serverTime: string
}

export async function fetchDashboardHealth(): Promise<DashboardHealth> {
  const response = await axiosClient.get<ApiResponse<DashboardHealth>>('/api/v1/health')
  if (!response.data.data) {
    throw new Error('Backend returned an empty health payload.')
  }
  return response.data.data
}
