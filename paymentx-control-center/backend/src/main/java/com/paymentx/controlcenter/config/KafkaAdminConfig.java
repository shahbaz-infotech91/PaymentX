package com.paymentx.controlcenter.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * ENGLISH: Builds the one real Kafka AdminClient this backend uses for
 * read-only broker introspection (topics, partitions, consumer
 * groups, offsets) - never a producer or consumer, since this module
 * has no business event to publish or consume. What it does: points
 * at the real bootstrap-servers from ControlCenterProperties.Kafka,
 * with the request timeout bounded by admin-client-timeout-ms so a
 * broker-side stall cannot hang a monitoring request indefinitely.
 * Spring destroys this bean via its inferred close() method on context
 * shutdown. Why it exists: AdminClient needs its own bean/lifecycle,
 * distinct from the RestTemplate beans in HttpClientConfig.
 *
 * HINGLISH: Wahi ek real Kafka AdminClient banata hai jo ye backend
 * read-only broker introspection (topics, partitions, consumer
 * groups, offsets) ke liye use karta hai - kabhi producer ya consumer
 * nahi, kyunki is module ke paas publish ya consume karne ke liye
 * koi business event nahi hai. Ye kya karti hai: real bootstrap-
 * servers ko ControlCenterProperties.Kafka se point karta hai,
 * request timeout ko admin-client-timeout-ms se bound karte hue taaki
 * ek broker-side stall kisi monitoring request ko hamesha ke liye
 * hang na kar sake. Spring context shutdown par apne inferred close()
 * method se is bean ko destroy karta hai. Ye dashboard me kyu hai:
 * AdminClient ko apna khud ka bean/lifecycle chahiye,
 * HttpClientConfig ke RestTemplate beans se alag.
 */
@Configuration
public class KafkaAdminConfig {

    private final ControlCenterProperties properties;

    public KafkaAdminConfig(ControlCenterProperties properties) {
        this.properties = properties;
    }

    @Bean
    public AdminClient kafkaAdminClient() {
        var kafka = properties.getKafka();
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, kafka.getAdminClientTimeoutMs(),
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, kafka.getAdminClientTimeoutMs()
        );
        return AdminClient.create(config);
    }
}
