/**
 * ENGLISH: The real API client for the Phase 5 API Tester domain. What
 * it does: lists the backend's real, fixed endpoint allowlist and
 * proxies exactly one allowlisted call through it - there is no
 * function here that sends a URL to the backend, only
 * {service, method, path, queryParams, headers, body}, matching the
 * backend's own "no arbitrary URLs" SSRF defense (see backend
 * ApiTesterAllowlist.java). Why it exists: the frontend must never
 * call an arbitrary PaymentX (or non-PaymentX) host directly from the
 * browser. How it will communicate with the backend: real HTTP calls
 * to ApiTesterController.
 *
 * HINGLISH: Phase 5 ke API Tester domain ke liye real API client. Ye
 * kya karti hai: backend ka real, fixed endpoint allowlist list karta
 * hai aur usi ke through exactly ek allowlisted call proxy karta hai -
 * yahan koi function nahi hai jo backend ko ek URL bheje, sirf
 * {service, method, path, queryParams, headers, body}, backend ke apne
 * "arbitrary URLs nahi" SSRF defense se match karte hue (backend
 * ApiTesterAllowlist.java dekho). Ye dashboard me kyu hai: frontend ko
 * kabhi browser se directly kisi arbitrary (PaymentX ya non-PaymentX)
 * host ko call nahi karna chahiye. Backend se kaise connect hogi:
 * ApiTesterController ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface ApiTesterEndpointDescriptor {
  service: string
  method: string
  pathTemplate: string
  description: string
  destructive: boolean
}

export interface ApiTesterRequestPayload {
  service: string
  method: string
  path: string
  queryParams: Record<string, string>
  headers: Record<string, string>
  body: string | null
}

export interface ApiTesterResponsePayload {
  status: number
  statusText: string
  headers: Record<string, string>
  body: string | null
  responseTimeMillis: number
}

export async function fetchApiTesterEndpoints(): Promise<ApiTesterEndpointDescriptor[]> {
  const response = await axiosClient.get<ApiResponse<ApiTesterEndpointDescriptor[]>>('/api/v1/api-tester/endpoints')
  return response.data.data ?? []
}

export async function executeApiTesterRequest(payload: ApiTesterRequestPayload): Promise<ApiTesterResponsePayload> {
  const response = await axiosClient.post<ApiResponse<ApiTesterResponsePayload>>('/api/v1/api-tester/execute', payload)
  if (!response.data.data) throw new Error('Backend returned an empty API Tester response payload.')
  return response.data.data
}
