/**
 * ENGLISH: Reserved for Phase 2. What it does: nothing yet - this
 * package intentionally contains no classes in Phase 1. Why it exists:
 * the Phase 1 brief requires the client/ package to exist in the
 * project structure (it is where HTTP clients to the 9 existing
 * PaymentX services, plus Kafka/Redis/Postgres/RabbitMQ/Prometheus/
 * Zipkin admin clients, will live), but Phase 1 explicitly must not
 * connect to any of those systems yet - see
 * ControlCenterProperties.Services/Infrastructure for the configuration
 * shape those Phase 2 clients will inject. How it will communicate with
 * the backend: N/A in Phase 1.
 *
 * HINGLISH: Phase 2 ke liye reserved hai. Ye kya karti hai: abhi kuch
 * nahi - is package me Phase 1 me jaan-bujhkar koi class nahi hai. Ye
 * dashboard me kyu hai: Phase 1 ke brief ke hisaab se project structure
 * me client/ package exist karna zaroori hai (yahin par 9 existing
 * PaymentX services ke HTTP clients, plus Kafka/Redis/Postgres/
 * RabbitMQ/Prometheus/Zipkin admin clients rahenge), lekin Phase 1 me
 * in systems se connect karna explicitly mana hai - configuration shape
 * ke liye ControlCenterProperties.Services/Infrastructure dekho jise
 * Phase 2 ke clients inject karenge. Backend se kaise connect hogi:
 * Phase 1 me N/A.
 */
package com.paymentx.controlcenter.client;
