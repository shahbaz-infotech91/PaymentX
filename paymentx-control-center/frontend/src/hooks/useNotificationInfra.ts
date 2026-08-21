/**
 * ENGLISH: React Query hook wrapping notificationInfraService.ts's
 * real MailHog status call.
 *
 * HINGLISH: notificationInfraService.ts ki real MailHog status call
 * ko wrap karne wala React Query hook.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchMailHogStatus } from '../services/notificationInfraService'

export function useMailHogStatus() {
  return useQuery({ queryKey: ['mailhog-status'], queryFn: fetchMailHogStatus, refetchInterval: 30_000 })
}
