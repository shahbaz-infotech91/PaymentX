/**
 * ENGLISH: React Query hooks wrapping every logsService.ts call.
 *
 * HINGLISH: Har logsService.ts call ko wrap karne wale React Query
 * hooks.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchLogServices, fetchLogs, type LogQueryParams } from '../services/logsService'

export function useLogServices() {
  return useQuery({ queryKey: ['log-services'], queryFn: fetchLogServices })
}

export function useLogs(params: LogQueryParams, enabled = true) {
  return useQuery({
    queryKey: ['logs', params],
    queryFn: () => fetchLogs(params),
    enabled,
    refetchInterval: 15_000,
  })
}
