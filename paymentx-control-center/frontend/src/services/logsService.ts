/**
 * ENGLISH: The real API client for the Phase 4 Log Viewer domain.
 * What it does: lists the fixed, server-configured service allowlist
 * and runs a real, bounded, filtered query against the real tail of
 * each real service's real log file - mirrors backend dto/logs/
 * LogEntry.java exactly. There is no function here that could ever
 * request an entire, unbounded log file - `limit` is always sent and
 * the backend clamps it regardless. Why it exists: the frontend must
 * never read a log file directly. How it will communicate with the
 * backend: real HTTP calls to LogsController.
 *
 * HINGLISH: Phase 4 ke Log Viewer domain ke liye real API client. Ye
 * kya karti hai: fixed, server-configured service allowlist list karta
 * hai aur har real service ki real log file ke real tail ke against
 * ek real, bounded, filtered query chalata hai - backend dto/logs/
 * LogEntry.java ko exactly mirror karti hai. Yahan koi function nahi
 * hai jo kabhi ek poori, unbounded log file request kar sake - `limit`
 * hamesha bheja jaata hai aur backend ise regardless clamp karta hai.
 * Ye dashboard me kyu hai: frontend ko kabhi ek log file directly
 * padhni nahi chahiye. Backend se kaise connect hogi: LogsController
 * ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface LogEntry {
  service: string
  timestamp: string | null
  level: string
  correlationId: string | null
  traceId: string | null
  paymentReference: string | null
  participantId: string | null
  logger: string | null
  message: string
  structured: boolean
}

export interface LogQueryParams {
  service?: string
  level?: string
  correlationId?: string
  traceId?: string
  paymentReference?: string
  search?: string
  from?: string
  to?: string
  limit?: number
}

export async function fetchLogServices(): Promise<string[]> {
  const response = await axiosClient.get<ApiResponse<string[]>>('/api/v1/logs/services')
  return response.data.data ?? []
}

export async function fetchLogs(params: LogQueryParams): Promise<LogEntry[]> {
  const response = await axiosClient.get<ApiResponse<LogEntry[]>>('/api/v1/logs', { params })
  return response.data.data ?? []
}
