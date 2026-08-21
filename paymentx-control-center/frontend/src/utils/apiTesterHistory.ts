/**
 * ENGLISH: Bounded, local (browser localStorage) history of API Tester
 * calls - deliberately stores only non-sensitive summary fields
 * (service, method, path, real response status, real response time,
 * timestamp). What it does NOT do: it never stores request/response
 * headers or bodies - those are exactly where a real Authorization
 * token, X-Api-Key, or business payload would live, and the Phase 5
 * brief explicitly requires "do not store secrets" for this history.
 * Capped at MAX_ENTRIES, oldest entries silently dropped, so this can
 * never grow unbounded across a long browser session.
 *
 * HINGLISH: API Tester calls ki bounded, local (browser localStorage)
 * history - jaan-boojh kar sirf non-sensitive summary fields store
 * karta hai (service, method, path, real response status, real
 * response time, timestamp). Ye kya NAHI karta: ye kabhi
 * request/response headers ya bodies store nahi karta - yehi wo jagah
 * hai jahan ek real Authorization token, X-Api-Key, ya business
 * payload rehta, aur Phase 5 brief explicitly is history ke liye "do
 * not store secrets" maangta hai. MAX_ENTRIES tak capped, purani
 * entries silently drop ho jaati hain, taaki ye ek lambe browser
 * session ke across kabhi unbounded na badhe.
 */

const STORAGE_KEY = 'paymentx-control-center.api-tester-history'
const MAX_ENTRIES = 20

export interface ApiTesterHistoryEntry {
  service: string
  method: string
  path: string
  status: number | null
  responseTimeMillis: number | null
  succeeded: boolean
  timestamp: string
}

export function loadApiTesterHistory(): ApiTesterHistoryEntry[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function appendApiTesterHistory(entry: ApiTesterHistoryEntry): ApiTesterHistoryEntry[] {
  const next = [entry, ...loadApiTesterHistory()].slice(0, MAX_ENTRIES)
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // Storage full/unavailable - the in-memory result the user just saw is unaffected, only persistence is skipped.
  }
  return next
}

export function clearApiTesterHistory(): ApiTesterHistoryEntry[] {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
  return []
}
