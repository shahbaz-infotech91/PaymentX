/**
 * ENGLISH: The real API client for the Phase 4 Notification
 * infrastructure status domain. Kaam: calls the backend's real MailHog
 * status endpoint (reachability, real captured-message count, and a
 * real link to MailHog's own UI) - mirrors backend dto/notification/
 * MailHogStatus.java exactly. Why it exists: the frontend must never
 * call MailHog directly; this is the one, safe, server-configured
 * path. How it will communicate with the backend: real HTTP calls to
 * NotificationInfraController.
 *
 * HINGLISH: Phase 4 ke Notification infrastructure status domain ke
 * liye real API client. Ye kya karti hai: backend ke real MailHog
 * status endpoint ko call karta hai (reachability, real
 * captured-message count, aur MailHog ke apne UI ka ek real link) -
 * backend dto/notification/MailHogStatus.java ko exactly mirror karti
 * hai. Ye dashboard me kyu hai: frontend ko kabhi MailHog ko directly
 * call nahi karna chahiye; yehi ek, safe, server-configured raasta
 * hai. Backend se kaise connect hogi: NotificationInfraController ko
 * real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface MailHogStatus {
  reachable: boolean
  errorMessage: string | null
  messageCount: number | null
  webUiUrl: string | null
}

export async function fetchMailHogStatus(): Promise<MailHogStatus> {
  const response = await axiosClient.get<ApiResponse<MailHogStatus>>('/api/v1/notifications/mailhog')
  if (!response.data.data) throw new Error('Backend returned an empty MailHog status payload.')
  return response.data.data
}
