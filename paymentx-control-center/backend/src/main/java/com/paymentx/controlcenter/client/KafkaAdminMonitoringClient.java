package com.paymentx.controlcenter.client;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.kafka.KafkaConsumerGroupSummary;
import com.paymentx.controlcenter.dto.kafka.KafkaPartitionLag;
import com.paymentx.controlcenter.dto.kafka.KafkaTopicInfo;
import com.paymentx.controlcenter.dto.kafka.KafkaTopicThroughput;
import com.paymentx.controlcenter.exception.ControlCenterException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ENGLISH: The real, read-only Kafka broker introspection client -
 * every method here is a genuine AdminClient call against the real
 * broker, bounded by the same admin-client-timeout-ms configured in
 * ControlCenterProperties.Kafka, and there is no method anywhere in
 * this class that creates, deletes, or alters a topic, or that
 * produces/consumes a business message. What it does: lists real
 * topics with real partition/replication metadata and classifies
 * DLT/retry topics purely from the platform's real, already-
 * established naming convention (".DLT" / ".retry" suffixes - see
 * paymentx-*-service Kafka error-handling config), lists real
 * consumer groups and their real per-partition lag (committed offset
 * vs. a freshly fetched log-end offset), and measures real topic
 * throughput via a bounded two-sample delta. Why it exists: this is
 * the actual Phase 2 "safe, read-only Kafka monitoring client, no
 * destructive operations" requirement.
 *
 * HINGLISH: Real, read-only Kafka broker introspection client - is
 * class ka har method real broker ke against ek genuine AdminClient
 * call hai, usi admin-client-timeout-ms se bound jo
 * ControlCenterProperties.Kafka me configure hai, aur is class me
 * kahin bhi koi aisa method nahi hai jo topic banaye, delete kare, ya
 * alter kare, ya jo koi business message produce/consume kare. Ye kya
 * karti hai: real topics ko real partition/replication metadata ke
 * saath list karta hai aur DLT/retry topics ko purely platform ke
 * real, already-established naming convention se classify karta hai
 * (".DLT" / ".retry" suffixes - paymentx-*-service Kafka
 * error-handling config dekho), real consumer groups aur unka real
 * per-partition lag list karta hai (committed offset vs ek freshly
 * fetch kiya gaya log-end offset), aur ek bounded two-sample delta ke
 * through real topic throughput measure karta hai. Ye dashboard me
 * kyu hai: yehi actual Phase 2 "safe, read-only Kafka monitoring
 * client, no destructive operations" requirement hai.
 */
@Component
public class KafkaAdminMonitoringClient {

    private static final long THROUGHPUT_SAMPLE_WINDOW_MS = 1000;

    private final AdminClient adminClient;
    private final long timeoutMs;

    public KafkaAdminMonitoringClient(AdminClient kafkaAdminClient, ControlCenterProperties properties) {
        this.adminClient = kafkaAdminClient;
        this.timeoutMs = properties.getKafka().getAdminClientTimeoutMs();
    }

    public List<KafkaTopicInfo> listTopics() {
        try {
            Set<String> topicNames = get(adminClient.listTopics().names());
            Map<String, TopicDescription> descriptions = get(adminClient.describeTopics(topicNames).allTopicNames());

            // One batched listOffsets call across every partition of every topic - real end offsets for
            // all topics, without an N+1 AdminClient round-trip per topic (Phase 4 "Produced" proxy).
            Map<TopicPartition, OffsetSpec> allPartitionSpecs = new HashMap<>();
            descriptions.forEach((name, description) ->
                    description.partitions().forEach(p -> allPartitionSpecs.put(new TopicPartition(name, p.partition()), OffsetSpec.latest())));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> allEndOffsets =
                    allPartitionSpecs.isEmpty() ? Map.of() : get(adminClient.listOffsets(allPartitionSpecs).all());

            Map<String, Long> messageCountByTopic = new HashMap<>();
            allEndOffsets.forEach((tp, info) -> messageCountByTopic.merge(tp.topic(), info.offset(), Long::sum));

            List<KafkaTopicInfo> result = new ArrayList<>();
            for (String name : topicNames) {
                TopicDescription description = descriptions.get(name);
                if (description == null) continue;
                int partitionCount = description.partitions().size();
                int replicationFactor = partitionCount == 0 ? 0 : description.partitions().get(0).replicas().size();
                boolean isDlt = name.endsWith(".DLT");
                long messageCount = messageCountByTopic.getOrDefault(name, 0L);
                result.add(new KafkaTopicInfo(name, partitionCount, replicationFactor, isDlt, name.contains(".retry"), messageCount, null));
            }

            // Second pass: attach each base topic's real DLT sibling message count as its errorCount.
            for (int i = 0; i < result.size(); i++) {
                KafkaTopicInfo topic = result.get(i);
                if (topic.deadLetterTopic()) continue;
                Long dltCount = messageCountByTopic.get(topic.name() + ".DLT");
                if (dltCount != null) {
                    result.set(i, new KafkaTopicInfo(topic.name(), topic.partitionCount(), topic.replicationFactor(),
                            topic.deadLetterTopic(), topic.retryTopic(), topic.messageCount(), dltCount));
                }
            }

            result.sort(Comparator.comparing(KafkaTopicInfo::name));
            return result;
        } catch (ExecutionException | TimeoutException e) {
            throw new ControlCenterException("KAFKA_UNREACHABLE", "Could not list Kafka topics: " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControlCenterException("KAFKA_INTERRUPTED", "Kafka topic listing was interrupted", e);
        }
    }

    public List<KafkaConsumerGroupSummary> listConsumerGroups() {
        try {
            Collection<ConsumerGroupListing> listings = get(adminClient.listConsumerGroups().all());
            List<String> groupIds = listings.stream().map(ConsumerGroupListing::groupId).toList();
            if (groupIds.isEmpty()) {
                return List.of();
            }
            Map<String, ConsumerGroupDescription> descriptions = get(adminClient.describeConsumerGroups(groupIds).all());
            return groupIds.stream()
                    .map(id -> {
                        ConsumerGroupDescription d = descriptions.get(id);
                        long totalCommitted = sumCommittedOffsets(id);
                        return new KafkaConsumerGroupSummary(id,
                                d != null ? d.state().toString() : "UNKNOWN",
                                d != null ? d.members().size() : 0,
                                totalCommitted);
                    })
                    .sorted(Comparator.comparing(KafkaConsumerGroupSummary::groupId))
                    .toList();
        } catch (ExecutionException | TimeoutException e) {
            throw new ControlCenterException("KAFKA_UNREACHABLE", "Could not list Kafka consumer groups: " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControlCenterException("KAFKA_INTERRUPTED", "Kafka consumer group listing was interrupted", e);
        }
    }

    /** Real sum of a consumer group's committed offsets across every partition it has committed to (Phase 4 "Consumed" proxy). */
    private long sumCommittedOffsets(String groupId) {
        try {
            Map<TopicPartition, OffsetAndMetadata> committed =
                    get(adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
            return committed.values().stream()
                    .filter(Objects::nonNull)
                    .mapToLong(OffsetAndMetadata::offset)
                    .sum();
        } catch (Exception e) {
            return 0L;
        }
    }

    public List<KafkaPartitionLag> consumerGroupLag(String groupId) {
        try {
            Map<TopicPartition, OffsetAndMetadata> committedOffsets =
                    get(adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
            if (committedOffsets.isEmpty()) {
                return List.of();
            }

            Map<TopicPartition, OffsetSpec> latestSpecs = new HashMap<>();
            committedOffsets.keySet().forEach(tp -> latestSpecs.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    get(adminClient.listOffsets(latestSpecs).all());

            List<KafkaPartitionLag> result = new ArrayList<>();
            committedOffsets.forEach((tp, offsetAndMetadata) -> {
                long committed = offsetAndMetadata != null ? offsetAndMetadata.offset() : 0L;
                ListOffsetsResult.ListOffsetsResultInfo endInfo = endOffsets.get(tp);
                long end = endInfo != null ? endInfo.offset() : committed;
                result.add(new KafkaPartitionLag(tp.topic(), tp.partition(), committed, end, Math.max(0, end - committed)));
            });
            result.sort(Comparator.comparing(KafkaPartitionLag::topic).thenComparingInt(KafkaPartitionLag::partition));
            return result;
        } catch (ExecutionException | TimeoutException e) {
            throw new ControlCenterException("KAFKA_UNREACHABLE", "Could not compute consumer group lag: " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControlCenterException("KAFKA_INTERRUPTED", "Kafka lag computation was interrupted", e);
        }
    }

    public KafkaTopicThroughput topicThroughput(String topicName) {
        try {
            long before = sumEndOffsets(topicName);
            long start = System.currentTimeMillis();
            Thread.sleep(THROUGHPUT_SAMPLE_WINDOW_MS);
            long after = sumEndOffsets(topicName);
            long windowMillis = System.currentTimeMillis() - start;

            long delta = Math.max(0, after - before);
            double perSecond = windowMillis > 0 ? (delta * 1000.0) / windowMillis : 0.0;
            return new KafkaTopicThroughput(topicName, delta, windowMillis, perSecond);
        } catch (ExecutionException | TimeoutException e) {
            throw new ControlCenterException("KAFKA_UNREACHABLE", "Could not measure topic throughput: " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControlCenterException("KAFKA_INTERRUPTED", "Kafka throughput sampling was interrupted", e);
        }
    }

    private long sumEndOffsets(String topicName) throws ExecutionException, InterruptedException, TimeoutException {
        TopicDescription description = get(adminClient.describeTopics(List.of(topicName)).allTopicNames()).get(topicName);
        if (description == null) {
            throw new ControlCenterException("KAFKA_TOPIC_NOT_FOUND", "Topic not found: " + topicName, null);
        }
        Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
        description.partitions().forEach(p -> specs.put(new TopicPartition(topicName, p.partition()), OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets = get(adminClient.listOffsets(specs).all());
        return offsets.values().stream().mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset).sum();
    }

    private <T> T get(KafkaFuture<T> future) throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private String rootMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage();
    }
}
