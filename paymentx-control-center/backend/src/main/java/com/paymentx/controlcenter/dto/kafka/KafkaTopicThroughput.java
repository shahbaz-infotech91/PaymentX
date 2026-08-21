package com.paymentx.controlcenter.dto.kafka;

/**
 * ENGLISH: A real, live-measured throughput sample for one topic -
 * taken by reading the sum of partition log-end offsets twice, one
 * second apart, and dividing the real observed delta by the real
 * elapsed time. Zero messages produced during that window is a
 * genuine, honest 0 - never fabricated to look active.
 *
 * HINGLISH: Ek topic ke liye ek real, live-measured throughput sample
 * - partition log-end offsets ke sum ko do baar padh kar liya gaya,
 * ek second ke fasle par, aur real observed delta ko real elapsed
 * time se divide karke. Us window ke dauran zero messages produce
 * hona ek genuine, honest 0 hai - kabhi active dikhane ke liye
 * fabricate nahi kiya gaya.
 */
public record KafkaTopicThroughput(
        String topic,
        long messagesInWindow,
        long windowMillis,
        double messagesPerSecond
) {
}
