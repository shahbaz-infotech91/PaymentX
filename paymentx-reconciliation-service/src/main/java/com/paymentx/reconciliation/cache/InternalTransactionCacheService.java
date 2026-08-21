package com.paymentx.reconciliation.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reconciliation.config.ReconciliationProperties;
import com.paymentx.reconciliation.dto.InternalTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * WHY internal transactions are cached in Redis rather than only queried
 * from Payment Service's own database directly: Reconciliation Service
 * does not have (and should not be given) direct DB access to Payment
 * Service's schema - services own their data, cross-service reads go
 * through events/APIs, not shared databases. Every payment-lifecycle
 * Kafka event this service consumes updates this cache, so by the time a
 * settlement file arrives days later, the matching engine has a fast,
 * already-populated lookup without needing a synchronous call back to
 * Payment Service.
 *
 * WHY a 7-day TTL, not "cache forever": settlement typically lands
 * within days of a payment - a transaction never settled after a week
 * is a genuine reconciliation gap the batch process should surface as
 * MISSING, not something this cache needs unbounded memory for.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * InternalTransactionCacheService is a service in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.cache and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * InternalTransactionCacheService PaymentX ke reconciliation module ka ek service hai. Ye com.paymentx.reconciliation.cache package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class InternalTransactionCacheService {

    private static final String KEY_PREFIX = "reconciliation:internal-txn:";
    private static final String PENDING_SETTLEMENT_SET_KEY = "reconciliation:pending-settlement";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReconciliationProperties reconciliationProperties;

    public InternalTransactionCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                            ReconciliationProperties reconciliationProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.reconciliationProperties = reconciliationProperties;
    }

    public void put(InternalTransaction transaction) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + transaction.paymentId(),
                    objectMapper.writeValueAsString(transaction), reconciliationProperties.getCache().getInternalTransactionTtl());
        } catch (Exception e) {
            log.warn("Failed to cache internal transaction paymentId={}", transaction.paymentId(), e);
        }
    }

    public Optional<InternalTransaction> get(String paymentId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + paymentId);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, InternalTransaction.class));
        } catch (Exception e) {
            log.warn("Failed to read cached internal transaction paymentId={}", paymentId, e);
            return Optional.empty();
        }
    }

    /** Called when a PAYMENT_COMPLETED event is consumed - marks this
     *  transaction as "awaiting settlement confirmation" so a future
     *  batch can detect it as MISSING if no settlement record ever
     *  claims it. WHY a Redis Set rather than a Postgres table: this is
     *  transient working state, not permanent audit truth - the
     *  permanent truth is the ReconciliationRecord row a batch
     *  eventually writes once it classifies (MATCHED or MISSING) each
     *  pending payment. */
    public void trackPendingSettlement(String paymentId) {
        redisTemplate.opsForSet().add(PENDING_SETTLEMENT_SET_KEY, paymentId);
    }

    /** Called once a settlement record actually matches this paymentId -
     *  removes it from the pending set so it's never re-flagged as
     *  MISSING by a later batch. */
    public void clearPendingSettlement(String paymentId) {
        redisTemplate.opsForSet().remove(PENDING_SETTLEMENT_SET_KEY, paymentId);
    }

    public Set<String> getPendingSettlements() {
        Set<String> members = redisTemplate.opsForSet().members(PENDING_SETTLEMENT_SET_KEY);
        return members != null ? members : Set.of();
    }
}
