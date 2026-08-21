package com.paymentx.controlcenter.dto.rabbitmq;

/**
 * ENGLISH: Real facts from RabbitMQ management API's GET /api/overview
 * - broker/management version and real cluster-wide message-rate
 * totals, exactly as the broker reports them.
 *
 * HINGLISH: RabbitMQ management API ke GET /api/overview se real
 * facts - broker/management version aur real cluster-wide message-rate
 * totals, bilkul waise jaise broker report karta hai.
 */
public record RabbitMqOverview(
        String managementVersion,
        String rabbitmqVersion,
        String clusterName,
        Long totalConnections,
        Long totalChannels,
        Long totalQueues,
        Long totalMessages
) {
}
