/**
 * ENGLISH: React Query hooks wrapping apiTesterService.ts's calls.
 *
 * HINGLISH: apiTesterService.ts ki calls ko wrap karne wale React
 * Query hooks.
 */
import { useMutation, useQuery } from '@tanstack/react-query'
import { fetchApiTesterEndpoints, executeApiTesterRequest } from '../services/apiTesterService'

export function useApiTesterEndpoints() {
  return useQuery({ queryKey: ['api-tester-endpoints'], queryFn: fetchApiTesterEndpoints, staleTime: Infinity })
}

export function useExecuteApiTesterRequest() {
  return useMutation({ mutationFn: executeApiTesterRequest })
}
