package com.paymentx.controlcenter.dto.kafka;

/**
 * ENGLISH: One real (topic, partition) entry inside a consumer group's
 * offset report - the group's real last-committed offset, the
 * partition's real current log-end offset (fetched fresh via
 * AdminClient.listOffsets), and lag = endOffset - committedOffset,
 * computed here rather than trusted from any cached source.
 *
 * HINGLISH: Ek consumer group ke offset report ke andar ek real
 * (topic, partition) entry - group ka real last-committed offset, us
 * partition ka real current log-end offset (fresh AdminClient.
 * listOffsets se fetch kiya gaya), aur lag = endOffset -
 * committedOffset, yahan compute kiya gaya, kisi cached source se
 * trust nahi kiya gaya.
 */
public record KafkaPartitionLag(
        String topic,
        int partition,
        long committedOffset,
        long endOffset,
        long lag
) {
}
