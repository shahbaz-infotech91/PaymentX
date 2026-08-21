/**
 * ENGLISH: The React Query hook wrapping fetchAiHealth. What it does:
 * polls the real GET /api/v1/ai/health endpoint every 30s (matching
 * useHealthCheck.ts's existing interval) and exposes loading/error/data
 * state for components/ai/AIStatus.tsx to render - the real backend
 * status, never a hardcoded "Available". Why it exists: keeps
 * data-fetching concerns out of AIStatus/AiAssistantPage, matching how
 * every other feature in this app separates hooks from services. How
 * it will communicate with the backend: indirectly, via
 * services/aiService.ts's real HTTP call.
 *
 * HINGLISH: fetchAiHealth ko wrap karne wala React Query hook. Ye kya
 * karti hai: real GET /api/v1/ai/health endpoint ko har 30s me poll
 * karta hai (useHealthCheck.ts ke existing interval se match karte
 * hue) aur loading/error/data state expose karta hai taaki
 * components/ai/AIStatus.tsx ise render kar sake - real backend
 * status, kabhi ek hardcoded "Available" nahi. Ye dashboard me kyu
 * hai: data-fetching concerns ko AIStatus/AiAssistantPage se bahar
 * rakhta hai, matching karte hue ki is app ka har doosra feature hooks
 * ko services se kaise separate karta hai. Backend se kaise connect
 * hogi: indirectly, services/aiService.ts ki real HTTP call ke through.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchAiHealth } from '../services/aiService'

export function useAiHealth() {
  return useQuery({
    queryKey: ['ai-health'],
    queryFn: fetchAiHealth,
    refetchInterval: 30_000,
    retry: 1,
  })
}
