/**
 * ENGLISH: The real API client for the Phase 2 Redis monitoring
 * domain. What it does: calls the backend's real PING/INFO-backed
 * health, memory, and keyspace-category endpoints - every interface
 * mirrors a real backend record (see backend dto/redis/*.java). No
 * function here can ever retrieve a key's value. Why it exists: the
 * frontend must never connect to Redis directly. How it will
 * communicate with the backend: real HTTP calls to RedisController.
 *
 * HINGLISH: Phase 2 ke Redis monitoring domain ke liye real API
 * client. Ye kya karti hai: backend ke real PING/INFO-backed health,
 * memory, aur keyspace-category endpoints ko call karta hai - har
 * interface ek real backend record ko mirror karti hai (backend
 * dto/redis/*.java dekho). Yahan koi function kisi key ki value kabhi
 * retrieve nahi kar sakta. Ye dashboard me kyu hai: frontend ko kabhi
 * Redis se directly connect nahi karna chahiye. Backend se kaise
 * connect hogi: RedisController ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface RedisHealthStatus {
  reachable: boolean
  errorMessage: string | null
  pingLatencyMillis: number
  redisVersion: string | null
  role: string | null
  uptimeSeconds: number | null
  connectedClients: number | null
}

export interface RedisMemoryInfo {
  usedMemoryBytes: number
  usedMemoryHuman: string | null
  maxMemoryBytes: number
  memoryFragmentationRatio: number
}

export interface RedisKeyCategoryCount {
  prefix: string
  count: number
}

export interface RedisKeyspaceStats {
  totalKeyCount: number
  categories: RedisKeyCategoryCount[]
  scanTruncated: boolean
  keyspaceHits: number
  keyspaceMisses: number
  hitRatePercent: number | null
}

export async function fetchRedisHealth(): Promise<RedisHealthStatus> {
  const response = await axiosClient.get<ApiResponse<RedisHealthStatus>>('/api/v1/redis/health')
  if (!response.data.data) throw new Error('Backend returned an empty Redis health payload.')
  return response.data.data
}

export async function fetchRedisMemory(): Promise<RedisMemoryInfo> {
  const response = await axiosClient.get<ApiResponse<RedisMemoryInfo>>('/api/v1/redis/memory')
  if (!response.data.data) throw new Error('Backend returned an empty Redis memory payload.')
  return response.data.data
}

export async function fetchRedisKeyspace(): Promise<RedisKeyspaceStats> {
  const response = await axiosClient.get<ApiResponse<RedisKeyspaceStats>>('/api/v1/redis/keyspace')
  if (!response.data.data) throw new Error('Backend returned an empty Redis keyspace payload.')
  return response.data.data
}

export interface RedisKeySample {
  key: string
  /** Real remaining TTL in seconds - null when the key has no expiry (Redis TTL command returned -1), never fabricated. */
  ttlSeconds: number | null
}

export async function fetchRedisSamples(): Promise<RedisKeySample[]> {
  const response = await axiosClient.get<ApiResponse<RedisKeySample[]>>('/api/v1/redis/samples')
  return response.data.data ?? []
}
