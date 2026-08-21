package com.paymentx.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Auth Service.
 *
 * @SpringBootApplication is itself a composite of three annotations worth
 * knowing individually (you WILL be asked to explain this at Staff level):
 *   - @Configuration      : this class can define @Bean methods
 *   - @EnableAutoConfiguration : Spring Boot inspects the classpath
 *         (e.g. "spring-boot-starter-web is present") and auto-configures
 *         beans accordingly (embedded Tomcat, Jackson converters, etc.)
 *   - @ComponentScan      : scans this package and sub-packages for
 *         @Component/@Service/@Repository/@RestController beans.
 *
 * componentScan's default base package matters: because this class lives in
 * com.paymentx.auth, it will NOT automatically pick up beans from
 * com.paymentx.common (different package tree) unless we explicitly widen
 * the scan. We'll handle that below.
 */
@SpringBootApplication(scanBasePackages = {"com.paymentx.auth", "com.paymentx.common"})
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuthServiceApplication is a REST controller in the auth module of PaymentX. It lives in package com.paymentx.auth and participates in auth's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through auth's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuthServiceApplication PaymentX ke auth module ka ek REST controller hai. Ye com.paymentx.auth package me hai aur auth ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise auth ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
