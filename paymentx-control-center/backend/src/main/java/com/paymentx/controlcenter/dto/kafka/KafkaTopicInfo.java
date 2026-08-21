package com.paymentx.controlcenter.dto.kafka;

/**
 * ENGLISH: One real Kafka topic as reported live by the broker's
 * AdminClient - its real partition count and replication factor, and
 * whether its name matches this platform's real ".DLT" dead-letter
 * suffix convention (see DefaultErrorHandler +
 * DeadLetterPublishingRecoverer usage across the PaymentX consumer
 * services) or its declared-but-unused ".retry" suffix convention.
 * Topic names themselves come from the broker at request time - never
 * a hardcoded list - so a newly created topic shows up without a code
 * change. messageCount is the real sum of every partition's current
 * log-end offset (Phase 4 "Produced" proxy - a genuine, real count,
 * labeled as an approximation because Kafka retention/compaction can
 * remove old messages from the log without moving the end offset
 * backwards, so this is "highest offset ever produced" more precisely
 * than "messages currently retained"). errorCount is populated only
 * for a real base topic that has a real matching ".DLT" topic - it is
 * that DLT topic's own real messageCount (a genuine "how many messages
 * actually failed and were dead-lettered" signal), and is null when no
 * matching DLT topic exists (never fabricated as 0).
 *
 * HINGLISH: Ek real Kafka topic, broker ke AdminClient se live report
 * kiya gaya - iska real partition count aur replication factor, aur
 * kya iska naam is platform ke real ".DLT" dead-letter suffix
 * convention se match karta hai (PaymentX consumer services me
 * DefaultErrorHandler + DeadLetterPublishingRecoverer usage dekho) ya
 * uske declared-but-unused ".retry" suffix convention se. Topic names
 * khud broker se request time par aate hain - kabhi hardcoded list
 * nahi - isliye ek naya banaya gaya topic bina code change ke dikhta
 * hai. messageCount har partition ke real current log-end offset ka
 * real sum hai (Phase 4 ka "Produced" proxy - ek genuine, real count,
 * approximation isliye kaha gaya kyunki Kafka retention/compaction log
 * se purane messages ko end offset peeche kiye bina remove kar sakta
 * hai, isliye ye "kabhi bhi produce hua highest offset" ke roop me
 * zyada precise hai, "abhi retained messages" ke roop me nahi).
 * errorCount sirf us real base topic ke liye populate hota hai jiska
 * ek real matching ".DLT" topic ho - ye us DLT topic ka apna real
 * messageCount hai (ek genuine "kitne messages actually fail hue aur
 * dead-letter hue" signal), aur null hota hai jab koi matching DLT
 * topic exist na kare (kabhi 0 ke roop me fabricate nahi kiya jaata).
 */
public record KafkaTopicInfo(
        String name,
        int partitionCount,
        int replicationFactor,
        boolean deadLetterTopic,
        boolean retryTopic,
        long messageCount,
        Long errorCount
) {
}
