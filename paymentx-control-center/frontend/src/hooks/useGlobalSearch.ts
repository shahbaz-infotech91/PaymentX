/**
 * ENGLISH: React Query hook wrapping globalSearch. What it does: only
 * enabled once the caller has a non-blank, 2+ character query (mirrors
 * the backend's own MIN_QUERY_LENGTH guard, avoiding a wasted request
 * for every single keystroke); not polled - search is a one-shot,
 * user-triggered lookup, not a live dashboard tile.
 *
 * HINGLISH: globalSearch ko wrap karne wala React Query hook. Ye kya
 * karti hai: sirf tabhi enabled hota hai jab caller ke paas ek
 * non-blank, 2+ character query ho (backend ke apne MIN_QUERY_LENGTH
 * guard ko mirror karta hai, har single keystroke ke liye ek wasted
 * request se bachte hue); poll nahi hota - search ek one-shot,
 * user-triggered lookup hai, koi live dashboard tile nahi.
 */
import { useQuery } from '@tanstack/react-query'
import { globalSearch } from '../services/searchService'

export function useGlobalSearch(query: string) {
  const trimmed = query.trim()
  return useQuery({
    queryKey: ['global-search', trimmed],
    queryFn: () => globalSearch(trimmed),
    enabled: trimmed.length >= 2,
  })
}
