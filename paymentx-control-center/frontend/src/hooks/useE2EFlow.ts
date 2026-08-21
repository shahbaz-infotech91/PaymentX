/**
 * ENGLISH: React Query hooks wrapping every e2eService.ts call.
 * useE2ERun polls the real run snapshot every 1.5s (matching the
 * backend's own real poll-interval config) but stops polling the
 * moment overallStatus is no longer RUNNING - it never polls forever,
 * mirroring the backend's own bounded-timeout guarantee on the
 * frontend side too. refetchIntervalInBackground is explicitly true
 * here (Phase 12 Defect #1 remediation) - this app's global query
 * defaults (see queryClient.ts) leave refetchOnWindowFocus off, and
 * TanStack Query's own default refetchIntervalInBackground is false,
 * meaning a scheduled refetchInterval tick is silently SKIPPED
 * whenever the document is not the visible/focused tab
 * (focusManager.isFocused(), driven by the Page Visibility API) - live
 * confirmed via the query cache: fetchStatus stayed "idle" and data
 * stayed on its first, early snapshot for minutes while the tab was
 * backgrounded, even though the real backend had already reached a
 * real terminal state almost immediately. A live-progress view for a
 * short, bounded run must keep reflecting real backend truth
 * regardless of tab focus - a user who glances away for a moment
 * should not come back to a frozen "Running" state that never
 * self-corrects (nothing else re-triggers a fetch, since
 * refetchOnWindowFocus is also off). This override is scoped to this
 * one query only, not the global default, because most of this app's
 * other polls (health checks, metrics) have no such correctness
 * requirement and background polling them would be pure waste.
 *
 * HINGLISH: Har e2eService.ts call ko wrap karne wale React Query
 * hooks. useE2ERun real run snapshot ko har 1.5s me poll karta hai
 * (backend ke apne real poll-interval config se match karte hue),
 * lekin jaise hi overallStatus ab RUNNING na rahe, polling rok deta
 * hai - ye kabhi hamesha ke liye poll nahi karta, backend ki apni
 * bounded-timeout guarantee ko frontend side par bhi mirror karte
 * hue. refetchIntervalInBackground yahan explicitly true hai (Phase 12
 * Defect #1 remediation) - is app ke global query defaults
 * (queryClient.ts dekho) refetchOnWindowFocus off rakhte hain, aur
 * TanStack Query ka apna default refetchIntervalInBackground false hai,
 * matlab jab bhi document visible/focused tab na ho (Page Visibility
 * API se driven focusManager.isFocused()), ek scheduled refetchInterval
 * tick silently SKIP ho jaata hai - live confirm kiya gaya query cache
 * se: fetchStatus "idle" raha aur data apne pehle, early snapshot par
 * hi minutes tak atka raha jabki tab background me tha, jabki real
 * backend already ek real terminal state par almost turant pahunch
 * chuka tha. Ek short, bounded run ke liye live-progress view ko tab
 * focus se independent, real backend truth reflect karte rehna chahiye
 * - ek user jo thodi der ke liye tab se door dekhe, use wapas aakar ek
 * frozen "Running" state nahi milni chahiye jo kabhi khud theek na ho
 * (kuch aur fetch trigger nahi karta, kyunki refetchOnWindowFocus bhi
 * off hai). Ye override sirf is ek query tak scoped hai, global default
 * nahi, kyunki is app ke baaki polls (health checks, metrics) ko aisi
 * koi correctness requirement nahi hai aur unhe background me poll
 * karna pure waste hoga.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { startE2ERun, fetchE2ERun, fetchE2EHistory } from '../services/e2eService'
import type { E2ERunResult } from '../services/e2eService'

export function useStartE2ERun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: startE2ERun,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['e2e-history'] })
    },
  })
}

export function useE2ERun(runId: string | null) {
  return useQuery({
    queryKey: ['e2e-run', runId],
    queryFn: () => fetchE2ERun(runId as string),
    enabled: runId !== null,
    refetchInterval: (query) => {
      const data = query.state.data as E2ERunResult | undefined
      return data && data.overallStatus === 'RUNNING' ? 1500 : false
    },
    refetchIntervalInBackground: true,
  })
}

export function useE2EHistory() {
  return useQuery({ queryKey: ['e2e-history'], queryFn: fetchE2EHistory })
}
