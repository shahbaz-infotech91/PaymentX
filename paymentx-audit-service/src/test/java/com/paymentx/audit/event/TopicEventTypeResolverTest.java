package com.paymentx.audit.event;

import com.paymentx.audit.constant.AuditKafkaTopics;
import com.paymentx.audit.entity.EventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TopicEventTypeResolverTest is a JUnit test class in the audit module of PaymentX, package com.paymentx.audit.event. It is used within audit's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TopicEventTypeResolverTest PaymentX ke audit module ka ek JUnit test class hai, package com.paymentx.audit.event me. Ye audit ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class TopicEventTypeResolverTest {

    @Test
    void resolve_validationTopics_mapToValidationCompleted() {
        assertThat(TopicEventTypeResolver.resolve(AuditKafkaTopics.INSTANT_PAYMENT_VALIDATED)).isEqualTo(EventType.VALIDATION_COMPLETED);
        assertThat(TopicEventTypeResolver.resolve(AuditKafkaTopics.CARD_PAYMENT_VALIDATED)).isEqualTo(EventType.VALIDATION_COMPLETED);
        assertThat(TopicEventTypeResolver.resolve(AuditKafkaTopics.REAL_TIME_PAYMENT_VALIDATED)).isEqualTo(EventType.VALIDATION_COMPLETED);
    }

    @Test
    void resolve_paymentCompletedTopic_mapsToPaymentCompleted() {
        assertThat(TopicEventTypeResolver.resolve(AuditKafkaTopics.PAYMENT_COMPLETED)).isEqualTo(EventType.PAYMENT_COMPLETED);
    }

    @Test
    void resolve_routingTopics_mapCorrectly() {
        assertThat(TopicEventTypeResolver.resolve(AuditKafkaTopics.ROUTING_ROUTE_RESOLVED)).isEqualTo(EventType.PAYMENT_ROUTED);
        assertThat(TopicEventTypeResolver.resolve(AuditKafkaTopics.ROUTING_RULE_CHANGED)).isEqualTo(EventType.ROUTING_RULE_CHANGED);
    }

    @Test
    void resolve_unknownTopic_fallsBackToKafkaEvent() {
        assertThat(TopicEventTypeResolver.resolve("some.unmapped.topic")).isEqualTo(EventType.KAFKA_EVENT);
    }

    @Test
    void resolveSourceService_identifiesCorrectService() {
        assertThat(TopicEventTypeResolver.resolveSourceService(AuditKafkaTopics.INSTANT_PAYMENT_VALIDATED)).isEqualTo("validation-service");
        assertThat(TopicEventTypeResolver.resolveSourceService(AuditKafkaTopics.PAYMENT_COMPLETED)).isEqualTo("payment-service");
        assertThat(TopicEventTypeResolver.resolveSourceService(AuditKafkaTopics.ROUTING_RULE_CHANGED)).isEqualTo("routing-service");
    }
}
