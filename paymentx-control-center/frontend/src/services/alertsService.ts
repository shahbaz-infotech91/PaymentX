/**
 * ENGLISH: The real API client for Operational Alerts (Phase 3). What
 * it does: calls GET /api/v1/alerts and returns exactly what the
 * backend's AlertsService currently detects as true - mirrors
 * com.paymentx.controlcenter.dto.alerts.Alert exactly. An empty array
 * is a genuine "platform healthy" result, not an error.
 *
 * HINGLISH: Operational Alerts (Phase 3) ke liye real API client. Ye
 * kya karti hai: GET /api/v1/alerts call karta hai aur exactly wahi
 * return karta hai jo backend ka AlertsService abhi true detect karta
 * hai - com.paymentx.controlcenter.dto.alerts.Alert ko exactly mirror
 * karta hai. Ek empty array ek genuine "platform healthy" result hai,
 * error nahi.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export type AlertSeverity = 'CRITICAL' | 'WARNING'

export interface Alert {
  id: string
  severity: AlertSeverity
  category: string
  title: string
  message: string
  detectedAt: string
}

export async function fetchAlerts(): Promise<Alert[]> {
  const response = await axiosClient.get<ApiResponse<Alert[]>>('/api/v1/alerts')
  return response.data.data ?? []
}
