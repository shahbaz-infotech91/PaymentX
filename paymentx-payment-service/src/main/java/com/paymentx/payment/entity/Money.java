package com.paymentx.payment.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * DDD value object: amount and currency are ALWAYS meaningful together,
 * never independently - "100" alone means nothing without knowing if it's
 * USD or JPY. Bundling them into one immutable type makes that invariant
 * impossible to violate by construction, instead of relying on every call
 * site to remember to pass both and keep them in sync.
 *
 * WHY @Embeddable rather than a separate joined table: Money has no
 * identity of its own (two Money(100, "USD") instances are interchangeable
 * - see equals() below) and never exists independently of its owning
 * entity (Payment or PaymentSettlement). That is the textbook definition
 * of a JPA embeddable value type, not an entity.
 *
 * WHY validation lives in the constructor, not a separate validate()
 * method: an invalid Money should be UNREPRESENTABLE, not merely
 * detectable after construction. This is the core DDD "make illegal
 * states unrepresentable" principle - a Money instance that exists at all
 * is guaranteed valid everywhere it's used.
 */
@Embeddable
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Money is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Money PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class Money {

    private static final Pattern ISO_4217_PATTERN = Pattern.compile("^[A-Z]{3}$");

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    protected Money() {
        // JPA requires a no-arg constructor, but it must never be used
        // directly by application code - protected (not public) keeps it
        // accessible to Hibernate's reflection-based instantiation while
        // signaling to other developers "use Money.of() instead."
    }

    private Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Money amount must be positive, got: " + amount);
        }
        if (currency == null || !ISO_4217_PATTERN.matcher(currency).matches()) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO 4217 code, got: " + currency);
        }
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * WHY same-currency-only addition, throwing rather than silently
     * converting: Money is not the place to embed FX conversion logic
     * (that belongs to a dedicated exchange-rate service, if PaymentX ever
     * needs multi-currency settlement). Adding USD + EUR without an
     * explicit conversion step is a silent correctness bug waiting to
     * happen - failing loudly here is the correct behavior.
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on Money with different currencies: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        // compareTo, not equals, on the BigDecimal: BigDecimal.equals()
        // treats 100.0 and 100.00 as DIFFERENT (different scale) - a
        // well-known BigDecimal gotcha. Money(100.0, "USD") and
        // Money(100.00, "USD") represent the SAME value and must be equal.
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        // Use stripTrailingZeros() so hashCode is consistent with the
        // compareTo-based equals() above - two BigDecimals that compareTo
        // equal but have different scale must hash identically, or Money
        // breaks as a HashMap/HashSet key.
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
