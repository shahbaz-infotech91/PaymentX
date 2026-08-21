/**
 * ENGLISH: The real API client for the Phase 5 E2E Payment Flow
 * domain. What it does: starts one real, bounded E2E run, polls one
 * run's real current snapshot, and lists the bounded real run history
 * - every interface mirrors a real backend record (see backend
 * dto/e2e/*.java). No function here computes a stage status itself -
 * every value shown by this dashboard for a run came straight from a
 * real E2EFlowService response. Why it exists: the frontend must never
 * simulate a payment flow's progress. How it will communicate with the
 * backend: real HTTP calls to E2EController.
 *
 * HINGLISH: Phase 5 ke E2E Payment Flow domain ke liye real API
 * client. Ye kya karti hai: ek real, bounded E2E run start karta hai,
 * ek run ka real current snapshot poll karta hai, aur bounded real run
 * history list karta hai - har interface ek real backend record ko
 * mirror karti hai (backend dto/e2e/*.java dekho). Yahan koi function
 * khud kisi stage ka status compute nahi karta - is dashboard ka har
 * value jo ek run ke liye dikhaya jaata hai seedhe ek real
 * E2EFlowService response se aata hai. Ye dashboard me kyu hai:
 * frontend ko kabhi ek payment flow ki progress simulate nahi karni
 * chahiye. Backend se kaise connect hogi: E2EController ko real HTTP
 * calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export type E2EStageStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT'

export interface E2EStage {
  name: string
  status: E2EStageStatus
  detail: string | null
  occurredAt: string | null
}

export interface E2ERunResult {
  runId: string
  paymentReference: string
  correlationId: string
  traceId: string | null
  overallStatus: E2EStageStatus
  startedAt: string
  completedAt: string | null
  durationMillis: number
  failureReason: string | null
  stages: E2EStage[]
}

export async function startE2ERun(): Promise<E2ERunResult> {
  const response = await axiosClient.post<ApiResponse<E2ERunResult>>('/api/v1/e2e/run')
  if (!response.data.data) throw new Error('Backend returned an empty E2E run payload.')
  return response.data.data
}

export async function fetchE2ERun(runId: string): Promise<E2ERunResult> {
  const response = await axiosClient.get<ApiResponse<E2ERunResult>>(`/api/v1/e2e/run/${encodeURIComponent(runId)}`)
  if (!response.data.data) throw new Error('Backend returned an empty E2E run payload.')
  return response.data.data
}

export async function fetchE2EHistory(): Promise<E2ERunResult[]> {
  const response = await axiosClient.get<ApiResponse<E2ERunResult[]>>('/api/v1/e2e/history')
  return response.data.data ?? []
}
