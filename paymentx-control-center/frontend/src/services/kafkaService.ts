/**
 * ENGLISH: The real API client for the Phase 2 Kafka monitoring
 * domain. What it does: calls the backend's real, read-only
 * AdminClient-backed endpoints for topics (with real DLT/retry
 * classification), consumer groups, per-group partition lag, and a
 * live-measured topic throughput sample - every interface mirrors a
 * real backend record (see backend dto/kafka/*.java). Why it exists:
 * the frontend must never talk to the Kafka broker directly. How it
 * will communicate with the backend: real HTTP calls to
 * KafkaController.
 *
 * HINGLISH: Phase 2 ke Kafka monitoring domain ke liye real API
 * client. Ye kya karti hai: backend ke real, read-only
 * AdminClient-backed endpoints ko call karta hai topics (real
 * DLT/retry classification ke saath), consumer groups, per-group
 * partition lag, aur ek live-measured topic throughput sample ke
 * liye - har interface ek real backend record ko mirror karti hai
 * (backend dto/kafka/*.java dekho). Ye dashboard me kyu hai: frontend
 * ko kabhi Kafka broker se directly baat nahi karni chahiye. Backend
 * se kaise connect hogi: KafkaController ko real HTTP calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface KafkaTopicInfo {
  name: string
  partitionCount: number
  replicationFactor: number
  deadLetterTopic: boolean
  retryTopic: boolean
  /** Real sum of every partition's current log-end offset - the "Produced" proxy (see backend KafkaTopicInfo javadoc). */
  messageCount: number
  /** Real message count of this topic's matching ".DLT" sibling - the "Errors" proxy; null when no DLT topic exists. */
  errorCount: number | null
}

export interface KafkaConsumerGroupSummary {
  groupId: string
  state: string
  memberCount: number
  /** Real sum of this group's committed offsets across every partition - the "Consumed" proxy. */
  totalCommittedOffset: number
}

export interface KafkaPartitionLag {
  topic: string
  partition: number
  committedOffset: number
  endOffset: number
  lag: number
}

export interface KafkaTopicThroughput {
  topic: string
  messagesInWindow: number
  windowMillis: number
  messagesPerSecond: number
}

export async function fetchKafkaTopics(): Promise<KafkaTopicInfo[]> {
  const response = await axiosClient.get<ApiResponse<KafkaTopicInfo[]>>('/api/v1/kafka/topics')
  return response.data.data ?? []
}

export async function fetchKafkaConsumerGroups(): Promise<KafkaConsumerGroupSummary[]> {
  const response = await axiosClient.get<ApiResponse<KafkaConsumerGroupSummary[]>>('/api/v1/kafka/consumer-groups')
  return response.data.data ?? []
}

export async function fetchKafkaConsumerGroupLag(groupId: string): Promise<KafkaPartitionLag[]> {
  const response = await axiosClient.get<ApiResponse<KafkaPartitionLag[]>>(`/api/v1/kafka/consumer-groups/${encodeURIComponent(groupId)}/lag`)
  return response.data.data ?? []
}

export async function fetchKafkaTopicThroughput(topicName: string): Promise<KafkaTopicThroughput> {
  const response = await axiosClient.get<ApiResponse<KafkaTopicThroughput>>(`/api/v1/kafka/topics/${encodeURIComponent(topicName)}/throughput`)
  if (!response.data.data) throw new Error('Backend returned an empty throughput payload.')
  return response.data.data
}
