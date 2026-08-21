/**
 * ENGLISH: React Query hooks wrapping every redisService.ts call -
 * health polled every 30s like the dashboard's own health check;
 * memory/keyspace fetched on the default staleness policy since a
 * bounded SCAN is more expensive than a PING.
 *
 * HINGLISH: Har redisService.ts call ko wrap karne wale React Query
 * hooks - health har 30s me poll hoti hai, dashboard ke apne health
 * check jaisi; memory/keyspace default staleness policy par fetch
 * hote hain kyunki ek bounded SCAN ek PING se zyada expensive hai.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchRedisHealth, fetchRedisMemory, fetchRedisKeyspace, fetchRedisSamples } from '../services/redisService'

export function useRedisHealth() {
  return useQuery({ queryKey: ['redis-health'], queryFn: fetchRedisHealth, refetchInterval: 30_000 })
}

export function useRedisMemory() {
  return useQuery({ queryKey: ['redis-memory'], queryFn: fetchRedisMemory })
}

export function useRedisKeyspace() {
  return useQuery({ queryKey: ['redis-keyspace'], queryFn: fetchRedisKeyspace })
}

export function useRedisSamples() {
  return useQuery({ queryKey: ['redis-samples'], queryFn: fetchRedisSamples })
}
