/**
 * ENGLISH: React Query hooks wrapping every rabbitMqService.ts call.
 *
 * HINGLISH: Har rabbitMqService.ts call ko wrap karne wale React
 * Query hooks.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchRabbitMqOverview, fetchRabbitMqConnections, fetchRabbitMqQueues, fetchRabbitMqBindings } from '../services/rabbitMqService'

export function useRabbitMqOverview() {
  return useQuery({ queryKey: ['rabbitmq-overview'], queryFn: fetchRabbitMqOverview })
}

export function useRabbitMqConnections() {
  return useQuery({ queryKey: ['rabbitmq-connections'], queryFn: fetchRabbitMqConnections })
}

export function useRabbitMqQueues() {
  return useQuery({ queryKey: ['rabbitmq-queues'], queryFn: fetchRabbitMqQueues })
}

export function useRabbitMqBindings() {
  return useQuery({ queryKey: ['rabbitmq-bindings'], queryFn: fetchRabbitMqBindings })
}
