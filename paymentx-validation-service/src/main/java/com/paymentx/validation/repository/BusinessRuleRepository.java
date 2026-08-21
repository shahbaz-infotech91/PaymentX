package com.paymentx.validation.repository;

import com.paymentx.validation.entity.BusinessRule;
import com.paymentx.validation.entity.Scheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BusinessRuleRepository is a interface in the validation module of PaymentX. It lives in package com.paymentx.validation.repository and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BusinessRuleRepository PaymentX ke validation module ka ek interface hai. Ye com.paymentx.validation.repository package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, Long> {

    @Query("SELECT r FROM BusinessRule r WHERE r.active = true AND (r.scheme = :scheme OR r.scheme = com.paymentx.validation.entity.Scheme.ALL)")
    List<BusinessRule> findActiveRulesForScheme(@Param("scheme") Scheme scheme);
}
