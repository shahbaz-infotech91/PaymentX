/**
 * ENGLISH: The real API client for Global Search (Phase 3). What it
 * does: calls GET /api/v1/search?q=... and unwraps the real results -
 * mirrors com.paymentx.controlcenter.dto.search.SearchResult exactly.
 * Why it exists: the header's search box must never query Postgres
 * directly; this is the only path. How it will communicate with the
 * backend: real HTTP call to SearchController.
 *
 * HINGLISH: Global Search (Phase 3) ke liye real API client. Ye kya
 * karti hai: GET /api/v1/search?q=... call karta hai aur real results
 * unwrap karta hai - com.paymentx.controlcenter.dto.search.
 * SearchResult ko exactly mirror karta hai. Ye dashboard me kyu hai:
 * header ke search box ko kabhi Postgres directly query nahi karna
 * chahiye; yehi ek raasta hai. Backend se kaise connect hogi:
 * SearchController ko real HTTP call.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export type SearchResultType = 'PAYMENT' | 'PARTICIPANT' | 'SETTLEMENT_FILE'

export interface SearchResult {
  type: SearchResultType
  key: string
  title: string
  subtitle: string
}

export async function globalSearch(query: string): Promise<SearchResult[]> {
  const response = await axiosClient.get<ApiResponse<SearchResult[]>>('/api/v1/search', { params: { q: query } })
  return response.data.data ?? []
}
