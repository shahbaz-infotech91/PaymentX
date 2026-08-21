/**
 * ENGLISH: The real API client for the Phase 2 Zipkin domain. What it
 * does: real service list and real trace lookup by Trace ID - every
 * interface mirrors a real backend record (see backend dto/zipkin/
 * *.java). Why it exists: the frontend must never call Zipkin
 * directly. How it will communicate with the backend: real HTTP calls
 * to ZipkinController.
 *
 * HINGLISH: Phase 2 ke Zipkin domain ke liye real API client. Ye kya
 * karti hai: real service list aur Trace ID se real trace lookup -
 * har interface ek real backend record ko mirror karti hai (backend
 * dto/zipkin/*.java dekho). Ye dashboard me kyu hai: frontend ko
 * kabhi Zipkin ko directly call nahi karna chahiye. Backend se kaise
 * connect hogi: ZipkinController ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface ZipkinSpan {
  traceId: string
  spanId: string
  parentId: string | null
  name: string | null
  serviceName: string | null
  kind: string | null
  timestampEpochMicros: number | null
  durationMicros: number | null
  tags: Record<string, string>
}

export interface ZipkinTraceDetail {
  traceId: string
  spans: ZipkinSpan[]
  services: string[]
  earliestTimestampEpochMicros: number | null
  totalDurationMicros: number
  status: string
}

export async function fetchZipkinServices(): Promise<string[]> {
  const response = await axiosClient.get<ApiResponse<string[]>>('/api/v1/zipkin/services')
  return response.data.data ?? []
}

export async function fetchZipkinTrace(traceId: string): Promise<ZipkinTraceDetail> {
  const response = await axiosClient.get<ApiResponse<ZipkinTraceDetail>>(`/api/v1/zipkin/traces/${encodeURIComponent(traceId)}`)
  if (!response.data.data) throw new Error('Backend returned an empty trace payload.')
  return response.data.data
}
