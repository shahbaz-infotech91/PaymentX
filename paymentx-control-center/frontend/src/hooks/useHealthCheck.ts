/**
 * ENGLISH: The React Query hook wrapping fetchDashboardHealth. What it
 * does: polls the backend's health endpoint every 30s and exposes
 * loading/error/data state the way every consumer (Footer status area,
 * HealthIndicator) needs. Why it exists: keeps data-fetching concerns
 * out of components - components call this hook and render whatever
 * state it reports, they never call axios directly. How it will
 * communicate with the backend: indirectly, via
 * services/healthService.ts's real HTTP call.
 *
 * HINGLISH: fetchDashboardHealth ko wrap karne wala React Query hook.
 * Ye kya karti hai: backend ke health endpoint ko har 30s me poll
 * karta hai aur loading/error/data state expose karta hai jaisa har
 * consumer (Footer status area, HealthIndicator) ko chahiye. Ye
 * dashboard me kyu hai: data-fetching concerns ko components se bahar
 * rakhta hai - components ye hook call karte hain aur jo bhi state ye
 * report kare wahi render karte hain, wo kabhi seedhe axios call nahi
 * karte. Backend se kaise connect hogi: indirectly,
 * services/healthService.ts ki real HTTP call ke through.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchDashboardHealth } from '../services/healthService'

export function useHealthCheck() {
  return useQuery({
    queryKey: ['dashboard-health'],
    queryFn: fetchDashboardHealth,
    refetchInterval: 30_000,
  })
}
