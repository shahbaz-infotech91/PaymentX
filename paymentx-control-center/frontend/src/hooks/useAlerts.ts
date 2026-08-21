/**
 * ENGLISH: React Query hook wrapping fetchAlerts - the "alerts" tier
 * of Phase 3's tiered polling policy, refreshed every 25s. Distinct
 * from the 30s health/metrics tier and the 20s transactions tier, so
 * every real-time surface isn't hammering the backend on the same
 * clock tick.
 *
 * HINGLISH: fetchAlerts ko wrap karne wala React Query hook - Phase
 * 3 ki tiered polling policy ka "alerts" tier, har 25s me refresh
 * hota hai. 30s health/metrics tier aur 20s transactions tier se alag,
 * taaki har real-time surface ek hi clock tick par backend ko hammer
 * na kare.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchAlerts } from '../services/alertsService'

export function useAlerts() {
  return useQuery({ queryKey: ['alerts'], queryFn: fetchAlerts, refetchInterval: 25_000 })
}
