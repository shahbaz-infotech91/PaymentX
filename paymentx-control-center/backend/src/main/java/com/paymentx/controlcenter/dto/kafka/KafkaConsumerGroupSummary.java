package com.paymentx.controlcenter.dto.kafka;

/**
 * ENGLISH: One real Kafka consumer group as reported live by the
 * broker (group ID, real coordinator-reported state such as Stable/
 * Empty/Dead, and real active member count). totalCommittedOffset is
 * the real sum of this group's committed offset across every
 * partition it has ever committed to (Phase 4 "Consumed" proxy) - a
 * genuine, real number read fresh from the broker on every call, not
 * a running total this backend tracks itself.
 *
 * HINGLISH: Ek real Kafka consumer group, broker se live report kiya
 * gaya (group ID, real coordinator-reported state jaise
 * Stable/Empty/Dead, aur real active member count). totalCommittedOffset
 * is group ke committed offset ka real sum hai har us partition ke
 * across jisme ise kabhi commit hua ho (Phase 4 ka "Consumed" proxy) -
 * ek genuine, real number jo har call par broker se fresh padha jaata
 * hai, koi running total nahi jo ye backend khud track kare.
 */
public record KafkaConsumerGroupSummary(
        String groupId,
        String state,
        int memberCount,
        long totalCommittedOffset
) {
}
