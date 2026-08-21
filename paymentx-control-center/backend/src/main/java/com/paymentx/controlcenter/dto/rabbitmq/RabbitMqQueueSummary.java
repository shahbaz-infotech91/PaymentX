package com.paymentx.controlcenter.dto.rabbitmq;

/**
 * ENGLISH: One real queue as reported live by RabbitMQ's management
 * API (GET /api/queues) - real ready/unacknowledged/total message
 * counts and real publish/deliver rates from the broker's own stats,
 * plus a real-name-based dlq/retry classification (matches "dlq",
 * "dead-letter", or "retry" in the queue name - the platform's real
 * RabbitMQ usage is infrastructure-only with no PaymentX application
 * queues at the time this was built, so an empty dlq/retry
 * classification here is an honest reflection of that, not a bug).
 *
 * HINGLISH: RabbitMQ ke management API (GET /api/queues) se live
 * report ki gayi ek real queue - broker ke apne stats se real
 * ready/unacknowledged/total message counts aur real publish/deliver
 * rates, plus ek real-name-based dlq/retry classification (queue naam
 * me "dlq", "dead-letter", ya "retry" match karta hai - platform ka
 * real RabbitMQ usage infrastructure-only hai, is build ke waqt koi
 * PaymentX application queues nahi thi, isliye yahan ek empty
 * dlq/retry classification uska honest reflection hai, koi bug nahi).
 */
public record RabbitMqQueueSummary(
        String name,
        String vhost,
        String state,
        boolean durable,
        int consumers,
        long messagesReady,
        long messagesUnacknowledged,
        long messagesTotal,
        double publishRatePerSecond,
        double deliverRatePerSecond,
        boolean deadLetterQueue,
        boolean retryQueue
) {
}
