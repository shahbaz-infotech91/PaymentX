/**
 * ENGLISH: The real API client for the Phase 2 RabbitMQ monitoring
 * domain. What it does: calls the backend's real Management API
 * proxy endpoints for overview, connections, channels, queues (with
 * real ready/unacknowledged counts and dlq/retry classification), and
 * bindings - every interface mirrors a real backend record (see
 * backend dto/rabbitmq/*.java). Why it exists: the frontend must
 * never call RabbitMQ's management API directly (it would need real
 * broker credentials in the browser). How it will communicate with
 * the backend: real HTTP calls to RabbitMqController.
 *
 * HINGLISH: Phase 2 ke RabbitMQ monitoring domain ke liye real API
 * client. Ye kya karti hai: backend ke real Management API proxy
 * endpoints ko overview, connections, channels, queues (real
 * ready/unacknowledged counts aur dlq/retry classification ke saath),
 * aur bindings ke liye call karta hai - har interface ek real backend
 * record ko mirror karti hai (backend dto/rabbitmq/*.java dekho). Ye
 * dashboard me kyu hai: frontend ko kabhi RabbitMQ ke management API
 * ko directly call nahi karna chahiye (isme browser me real broker
 * credentials chahiye hote). Backend se kaise connect hogi:
 * RabbitMqController ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface RabbitMqOverview {
  managementVersion: string | null
  rabbitmqVersion: string | null
  clusterName: string | null
  totalConnections: number | null
  totalChannels: number | null
  totalQueues: number | null
  totalMessages: number | null
}

export interface RabbitMqConnectionSummary {
  name: string | null
  state: string | null
  host: string | null
  port: number | null
  peerHost: string | null
  peerPort: number | null
  protocol: string | null
  user: string | null
  connectedAtEpochMillis: number | null
}

export interface RabbitMqQueueSummary {
  name: string
  vhost: string | null
  state: string | null
  durable: boolean
  consumers: number
  messagesReady: number
  messagesUnacknowledged: number
  messagesTotal: number
  publishRatePerSecond: number
  deliverRatePerSecond: number
  deadLetterQueue: boolean
  retryQueue: boolean
}

export interface RabbitMqBindingSummary {
  source: string
  destination: string
  destinationType: string
  routingKey: string
  vhost: string
}

export async function fetchRabbitMqOverview(): Promise<RabbitMqOverview> {
  const response = await axiosClient.get<ApiResponse<RabbitMqOverview>>('/api/v1/rabbitmq/overview')
  if (!response.data.data) throw new Error('Backend returned an empty RabbitMQ overview payload.')
  return response.data.data
}

export async function fetchRabbitMqConnections(): Promise<RabbitMqConnectionSummary[]> {
  const response = await axiosClient.get<ApiResponse<RabbitMqConnectionSummary[]>>('/api/v1/rabbitmq/connections')
  return response.data.data ?? []
}

export async function fetchRabbitMqQueues(): Promise<RabbitMqQueueSummary[]> {
  const response = await axiosClient.get<ApiResponse<RabbitMqQueueSummary[]>>('/api/v1/rabbitmq/queues')
  return response.data.data ?? []
}

export async function fetchRabbitMqBindings(): Promise<RabbitMqBindingSummary[]> {
  const response = await axiosClient.get<ApiResponse<RabbitMqBindingSummary[]>>('/api/v1/rabbitmq/bindings')
  return response.data.data ?? []
}
