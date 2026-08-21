package com.paymentx.controlcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ENGLISH: The Spring Boot entry point for the Control Center dashboard
 * backend. What it does: boots the web server and wires up every bean in
 * this module. Why it exists: the Control Center needs its own backend
 * process (separate JVM, separate port, separate deploy lifecycle) so it
 * can evolve independently of the 9 existing PaymentX business services -
 * Phase 1 deliberately keeps this module free of any compile-time
 * dependency on those services. How it will communicate with the
 * backend: N/A for this class itself - it IS the backend process; the
 * React frontend will call this backend's REST API (see the controller
 * package) over HTTP, and this backend will in turn call the real
 * PaymentX services/infrastructure starting in Phase 2 (see the client
 * package placeholders).
 *
 * HINGLISH: Ye Control Center dashboard backend ka Spring Boot entry
 * point hai. Ye kya karti hai: web server start karti hai aur is module
 * ke saare beans wire karti hai. Ye dashboard me kyu hai: Control Center
 * ko apna alag backend process chahiye (alag JVM, alag port, alag
 * deploy lifecycle) taaki ye 9 existing PaymentX business services se
 * independently evolve ho sake - Phase 1 jaan-bujhkar is module ko un
 * services par compile-time dependency se free rakhta hai. Backend se
 * kaise connect hogi: is class ke liye N/A - ye khud backend process
 * hai; React frontend is backend ke REST API ko HTTP ke through call
 * karega (controller package dekho), aur ye backend Phase 2 se real
 * PaymentX services/infrastructure ko call karega (client package ke
 * placeholders dekho).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ControlCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlCenterApplication.class, args);
    }
}
