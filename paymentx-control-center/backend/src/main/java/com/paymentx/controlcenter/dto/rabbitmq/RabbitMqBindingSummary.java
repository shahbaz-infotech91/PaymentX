package com.paymentx.controlcenter.dto.rabbitmq;

/**
 * ENGLISH: One real binding as reported live by RabbitMQ's management
 * API (GET /api/bindings) - which exchange routes to which
 * queue/exchange, via which routing key.
 *
 * HINGLISH: RabbitMQ ke management API (GET /api/bindings) se live
 * report ki gayi ek real binding - kaunsa exchange kaunse
 * queue/exchange tak route karta hai, kis routing key ke through.
 */
public record RabbitMqBindingSummary(
        String source,
        String destination,
        String destinationType,
        String routingKey,
        String vhost
) {
}
