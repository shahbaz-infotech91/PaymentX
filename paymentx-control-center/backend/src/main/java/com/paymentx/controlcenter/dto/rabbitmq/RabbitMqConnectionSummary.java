package com.paymentx.controlcenter.dto.rabbitmq;

/**
 * ENGLISH: One real client connection as reported live by RabbitMQ's
 * management API (GET /api/connections).
 *
 * HINGLISH: RabbitMQ ke management API (GET /api/connections) se live
 * report ki gayi ek real client connection.
 */
public record RabbitMqConnectionSummary(
        String name,
        String state,
        String host,
        Integer port,
        String peerHost,
        Integer peerPort,
        String protocol,
        String user,
        Long connectedAtEpochMillis
) {
}
