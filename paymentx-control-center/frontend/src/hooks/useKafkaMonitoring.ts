/**
 * ENGLISH: React Query hooks wrapping every kafkaService.ts call.
 * Throughput sampling takes ~1s on the backend (a real two-sample
 * delta measurement), so it's only fetched on demand (enabled: false
 * default, triggered by refetch()) rather than polled automatically.
 *
 * HINGLISH: Har kafkaService.ts call ko wrap karne wale React Query
 * hooks. Throughput sampling backend par ~1s leti hai (ek real
 * two-sample delta measurement), isliye ye sirf on demand fetch hoti
 * hai (default enabled: false, refetch() se trigger), automatically
 * poll nahi hoti.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchKafkaTopics, fetchKafkaConsumerGroups, fetchKafkaConsumerGroupLag, fetchKafkaTopicThroughput } from '../services/kafkaService'

export function useKafkaTopics() {
  return useQuery({ queryKey: ['kafka-topics'], queryFn: fetchKafkaTopics })
}

export function useKafkaConsumerGroups() {
  return useQuery({ queryKey: ['kafka-consumer-groups'], queryFn: fetchKafkaConsumerGroups })
}

export function useKafkaConsumerGroupLag(groupId: string | null) {
  return useQuery({
    queryKey: ['kafka-consumer-group-lag', groupId],
    queryFn: () => fetchKafkaConsumerGroupLag(groupId as string),
    enabled: groupId !== null,
  })
}

export function useKafkaTopicThroughput(topicName: string | null) {
  return useQuery({
    queryKey: ['kafka-topic-throughput', topicName],
    queryFn: () => fetchKafkaTopicThroughput(topicName as string),
    enabled: false,
  })
}
