package com.paymentx.controlcenter.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * ENGLISH: Builds one named, timeout-bounded RestTemplate per outbound
 * HTTP integration (PaymentX services, Prometheus, RabbitMQ management
 * API, Zipkin). What it does: applies each domain's own connect/read
 * timeout from ControlCenterProperties (never a shared, one-size-fits-
 * all timeout - Prometheus range queries genuinely need longer than a
 * simple Actuator health check), and attaches HTTP Basic auth to the
 * RabbitMQ client since its management API requires it. Why it exists:
 * "implement timeouts" is an explicit Phase 2 requirement - a client
 * with no read timeout can hang a request thread indefinitely if a
 * downstream system stalls; every RestTemplate here is built to fail
 * fast instead. How it will communicate with the backend: these ARE
 * the backend's outbound HTTP clients - every client/*Client.java
 * (except KafkaAdminMonitoringClient and RedisMonitoringClient, which
 * use their own native drivers) injects one of these beans.
 *
 * HINGLISH: Har outbound HTTP integration (PaymentX services,
 * Prometheus, RabbitMQ management API, Zipkin) ke liye ek named,
 * timeout-bounded RestTemplate banata hai. Ye kya karti hai: har
 * domain ka apna connect/read timeout ControlCenterProperties se
 * apply karta hai (kabhi ek shared, one-size-fits-all timeout nahi -
 * Prometheus range queries ko genuinely ek simple Actuator health
 * check se zyada time chahiye), aur RabbitMQ client par HTTP Basic
 * auth attach karta hai kyunki uske management API ko wo chahiye. Ye
 * dashboard me kyu hai: "implement timeouts" ek explicit Phase 2
 * requirement hai - agar client ka koi read timeout na ho toh agar
 * koi downstream system stall ho jaye toh ek request thread hamesha
 * ke liye hang ho sakti hai; yahan har RestTemplate fail-fast hone ke
 * liye banaya gaya hai. Backend se kaise connect hogi: yehi backend
 * ke outbound HTTP clients HAIN - har client/*Client.java (sivaay
 * KafkaAdminMonitoringClient aur RedisMonitoringClient ke, jo apne
 * native drivers use karte hain) inme se ek bean inject karta hai.
 */
@Configuration
public class HttpClientConfig {

    private final ControlCenterProperties properties;

    public HttpClientConfig(ControlCenterProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestTemplate paymentXServiceRestTemplate(RestTemplateBuilder builder) {
        var services = properties.getServices();
        return builder
                .connectTimeout(Duration.ofMillis(services.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(services.getReadTimeoutMs()))
                .build();
    }

    @Bean
    public RestTemplate prometheusRestTemplate(RestTemplateBuilder builder) {
        var prometheus = properties.getPrometheus();
        return builder
                .connectTimeout(Duration.ofMillis(prometheus.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(prometheus.getReadTimeoutMs()))
                .build();
    }

    @Bean
    public RestTemplate rabbitMqRestTemplate(RestTemplateBuilder builder) {
        var rabbitmq = properties.getRabbitmq();
        return builder
                .connectTimeout(Duration.ofMillis(rabbitmq.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(rabbitmq.getReadTimeoutMs()))
                .additionalInterceptors(new BasicAuthenticationInterceptor(rabbitmq.getUsername(), rabbitmq.getPassword()))
                .build();
    }

    @Bean
    public RestTemplate zipkinRestTemplate(RestTemplateBuilder builder) {
        var zipkin = properties.getZipkin();
        return builder
                .connectTimeout(Duration.ofMillis(zipkin.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(zipkin.getReadTimeoutMs()))
                .build();
    }

    @Bean
    public RestTemplate mailHogRestTemplate(RestTemplateBuilder builder) {
        var mailhog = properties.getMailhog();
        return builder
                .connectTimeout(Duration.ofMillis(mailhog.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(mailhog.getReadTimeoutMs()))
                .build();
    }

    /**
     * Phase 3.3 addition - a dedicated RestTemplate for Prompt Service/LLM Service calls, not a reuse of
     * paymentXServiceRestTemplate above: a real LLM generation call can legitimately take far longer than
     * the simple Actuator health checks that RestTemplate's 5000ms read timeout was tuned for (see
     * ControlCenterProperties.Ai's javadoc for the real default: 30000ms).
     */
    @Bean
    public RestTemplate aiServiceRestTemplate(RestTemplateBuilder builder) {
        var ai = properties.getAi();
        return builder
                .connectTimeout(Duration.ofMillis(ai.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(ai.getReadTimeoutMs()))
                .build();
    }
}
