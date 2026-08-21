/**
 * ENGLISH: The single configured TanStack React Query client. What it
 * does: sets sane defaults (no refetch storm on window focus, one
 * retry, 30s staleness, always-attempt networkMode - see the inline
 * comment below for the real Phase 6 bug that setting fixes) for
 * every query in this app. Why it exists:
 * server data (health checks, and every Phase 2 dashboard dataset)
 * belongs in React Query, not Redux - this is where that policy is
 * enforced once instead of per-hook. How it will communicate with the
 * backend: indirectly - every hook in src/hooks/ that calls
 * useQuery/useMutation runs through this client's caching/retry rules
 * when it calls a src/services/ function.
 *
 * HINGLISH: Ek hi configured TanStack React Query client. Ye kya
 * karti hai: is app ki har query ke liye sensible defaults set karta
 * hai (window focus par refetch storm nahi, ek retry, 30s staleness).
 * Ye dashboard me kyu hai: server data (health checks, aur har Phase 2
 * dashboard dataset) Redux me nahi, React Query me belong karta hai -
 * yahin par ye policy ek hi jagah enforce hoti hai, har hook me nahi.
 * Backend se kaise connect hogi: indirectly - src/hooks/ ka har hook
 * jo useQuery/useMutation call karta hai, jab wo ek src/services/
 * function call karta hai toh is client ke caching/retry rules ke
 * through guzarta hai.
 */
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,
      // ENGLISH: Phase 6 hardening - a real bug found via live browser verification. React Query's default
      // networkMode ('online') pauses a failed query's retry indefinitely whenever its own onlineManager
      // considers the browser offline - and in at least one real, reproducible browser session, that left
      // every query stuck showing neither a spinner nor this dashboard's own real ErrorState when the real
      // backend was genuinely unreachable (confirmed via the query cache: fetchStatus stayed "paused" forever,
      // never reaching "error"). 'always' makes every query attempt its real fetch regardless of that
      // heuristic - this app's own axios timeout/error handling (toApiError, every page's real ErrorState)
      // is the actual, correct signal for "can this backend be reached", not the browser's generic online/
      // offline detection, which was never a reliable proxy for "is THIS specific backend up" anyway.
      // HINGLISH: Phase 6 hardening - live browser verification se mila ek real bug. React Query ka default
      // networkMode ('online') ek failed query ke retry ko hamesha ke liye pause kar deta hai jab bhi uska
      // apna onlineManager browser ko offline samjhe - aur kam se kam ek real, reproducible browser session
      // me, isse har query na koi spinner na is dashboard ka apna real ErrorState dikhate hue stuck ho gayi
      // jab real backend genuinely unreachable tha (query cache se confirm kiya: fetchStatus hamesha "paused"
      // raha, kabhi "error" tak nahi pahuncha). 'always' har query ko us heuristic ko ignore karke apna real
      // fetch attempt karne deta hai - is app ka apna axios timeout/error handling (toApiError, har page ka
      // real ErrorState) hi actual, correct signal hai "ye backend reach ho sakta hai ya nahi" ke liye,
      // browser ki generic online/offline detection nahi, jo "kya YE specific backend up hai" ka kabhi
      // reliable proxy thi hi nahi.
      networkMode: 'always',
    },
  },
})
