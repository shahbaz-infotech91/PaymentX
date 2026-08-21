/**
 * ENGLISH: Phase 6 performance hardening - debounces a fast-changing
 * value (typically a search box's raw keystroke-by-keystroke state)
 * before it's allowed to feed a React Query queryKey. What it does:
 * returns the input value, but only after it has been stable for
 * `delayMs` - every search-driven page in this app (Audit, Notifications,
 * Reconciliation Records, the Database viewer's tabs, Logs) feeds this
 * debounced value into its query hook instead of the raw SearchBar
 * value, so a real backend request only fires once a user pauses
 * typing, not on every keystroke. Why it exists: without this, a
 * five-character search term fired five real, mostly-wasted requests
 * at the real backend/Postgres - a genuine, avoidable load pattern a
 * production dashboard should not have.
 *
 * HINGLISH: Phase 6 performance hardening - ek fast-changing value
 * (aam taur par ek search box ki raw keystroke-by-keystroke state) ko
 * debounce karta hai, use React Query queryKey ko feed karne se pehle.
 * Ye kya karti hai: input value return karta hai, lekin sirf tab jab
 * wo `delayMs` tak stable rahi ho - is app ka har search-driven page
 * (Audit, Notifications, Reconciliation Records, Database viewer ke
 * tabs, Logs) apne query hook me raw SearchBar value ke bajaye ye
 * debounced value feed karta hai, taaki ek real backend request sirf
 * tabhi fire ho jab user typing rok de, har keystroke par nahi. Ye
 * dashboard me kyu hai: iske bina, ek five-character search term real
 * backend/Postgres par paanch real, mostly-wasted requests fire karta
 * - ek genuine, avoidable load pattern jo ek production dashboard ke
 * paas nahi hona chahiye.
 */
import { useEffect, useState } from 'react'

export function useDebouncedValue<T>(value: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
