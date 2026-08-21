package com.paymentx.payment.producer;

import com.paymentx.payment.constant.KafkaTopics;
import com.paymentx.payment.event.PaymentDebitedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentDebitedProducer is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.producer and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentDebitedProducer PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.producer package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentDebitedProducer extends AbstractEventProducer<PaymentDebitedEvent> {

    public PaymentDebitedProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        super(kafkaTemplate);
    }

    public void publish(String traceId, PaymentDebitedEvent payload) {
        publish(KafkaTopics.PAYMENT_DEBITED, payload.getPaymentReference(),
                "PAYMENT_DEBITED", traceId, payload);
    }
}
