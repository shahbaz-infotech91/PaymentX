/**
 * Phase 4.7 - TanStack Query hooks for the AI Agent Control Center, mirroring
 * hooks/usePostgresData.ts's own "thin useQuery/useMutation wrapper" convention.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  executeAgent,
  fetchAgents,
  fetchExecutionDetail,
  fetchExecutionHistory,
} from '../services/agentService'
import type { AgentExecuteRequest, AgentExecutionFilterParams } from '../types/agent'

export function useAgentList() {
  return useQuery({ queryKey: ['agents'], queryFn: fetchAgents })
}

export function useExecuteAgent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: AgentExecuteRequest) => executeAgent(request),
    onSuccess: () => {
      // A fresh execution just landed in the real audit trail - invalidate so History reflects it
      // without the user needing a manual refresh.
      queryClient.invalidateQueries({ queryKey: ['agent-executions'] })
    },
  })
}

export function useExecutionHistory(page: number, size: number, filter: AgentExecutionFilterParams) {
  return useQuery({
    queryKey: ['agent-executions', page, size, filter],
    queryFn: () => fetchExecutionHistory(page, size, filter),
  })
}

export function useExecutionDetail(executionId: string | null) {
  return useQuery({
    queryKey: ['agent-execution-detail', executionId],
    queryFn: () => fetchExecutionDetail(executionId as string),
    enabled: executionId !== null,
  })
}
