/**
 * ENGLISH: The real API client for the Phase 2 Service Health domain.
 * What it does: calls the backend's GET /api/v1/services (all 9 real
 * PaymentX services, checked in parallel) and the per-service
 * liveness/readiness/info probes, unwrapping ApiResponse each time.
 * Every field here mirrors com.paymentx.controlcenter.dto.
 * ServiceHealthStatus exactly - never invents a status client-side.
 * Why it exists: the frontend must never call a PaymentX service's
 * Actuator endpoint directly (SSRF/architecture rule) - this is the
 * only path the UI has to real service health. How it will
 * communicate with the backend: real HTTP calls to
 * ServicesController.
 *
 * HINGLISH: Phase 2 ke Service Health domain ke liye real API client.
 * Ye kya karti hai: backend ka GET /api/v1/services (saare 9 real
 * PaymentX services, parallel me check kiye gaye) aur per-service
 * liveness/readiness/info probes call karta hai, har baar ApiResponse
 * unwrap karte hue. Yahan har field
 * com.paymentx.controlcenter.dto.ServiceHealthStatus ko exactly
 * mirror karti hai - kabhi client-side status invent nahi karti. Ye
 * dashboard me kyu hai: frontend ko kabhi kisi PaymentX service ke
 * Actuator endpoint ko directly call nahi karna chahiye (SSRF/
 * architecture rule) - real service health tak UI ka yehi ek raasta
 * hai. Backend se kaise connect hogi: ServicesController ko real HTTP
 * calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface ServiceHealthStatus {
  serviceSlug: string
  serviceName: string
  baseUrl: string
  status: string | null
  httpStatusCode: number | null
  responseTimeMillis: number
  errorMessage: string | null
  checkedAt: string
}

export async function fetchAllServicesHealth(): Promise<ServiceHealthStatus[]> {
  const response = await axiosClient.get<ApiResponse<ServiceHealthStatus[]>>('/api/v1/services')
  return response.data.data ?? []
}

export async function fetchServiceLiveness(slug: string): Promise<ServiceHealthStatus> {
  const response = await axiosClient.get<ApiResponse<ServiceHealthStatus>>(`/api/v1/services/${slug}/liveness`)
  if (!response.data.data) throw new Error('Backend returned an empty liveness payload.')
  return response.data.data
}

export async function fetchServiceReadiness(slug: string): Promise<ServiceHealthStatus> {
  const response = await axiosClient.get<ApiResponse<ServiceHealthStatus>>(`/api/v1/services/${slug}/readiness`)
  if (!response.data.data) throw new Error('Backend returned an empty readiness payload.')
  return response.data.data
}
