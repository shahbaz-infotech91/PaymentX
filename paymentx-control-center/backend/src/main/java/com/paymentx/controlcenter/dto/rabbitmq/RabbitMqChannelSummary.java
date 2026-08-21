package com.paymentx.controlcenter.dto.rabbitmq;

/**
 * ENGLISH: One real channel as reported live by RabbitMQ's management
 * API (GET /api/channels).
 *
 * HINGLISH: RabbitMQ ke management API (GET /api/channels) se live
 * report kiya gaya ek real channel.
 */
public record RabbitMqChannelSummary(
        String name,
        String connectionName,
        String state,
        Integer consumerCount,
        Long messagesUnacknowledged
) {
}
