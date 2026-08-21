package com.paymentx.validation.service;

import com.paymentx.validation.entity.BusinessRule;
import com.paymentx.validation.entity.Scheme;
import com.paymentx.validation.exception.BusinessRuleViolationException;
import com.paymentx.validation.repository.BusinessRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BusinessRuleValidationService is a service in the validation module of PaymentX. It lives in package com.paymentx.validation.service and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BusinessRuleValidationService PaymentX ke validation module ka ek service hai. Ye com.paymentx.validation.service package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class BusinessRuleValidationService {

    private final BusinessRuleRepository businessRuleRepository;

    public void validateAmount(Scheme scheme, BigDecimal amount) {
        List<BusinessRule> rules = businessRuleRepository.findActiveRulesForScheme(scheme);

        for (BusinessRule rule : rules) {
            if (!"AMOUNT_LIMIT".equals(rule.getRuleType())) {
                continue;
            }
            if (rule.getMinAmount() != null && amount.compareTo(rule.getMinAmount()) < 0) {
                throw new BusinessRuleViolationException(
                        "AMOUNT_BELOW_MINIMUM",
                        "Amount " + amount + " is below minimum " + rule.getMinAmount()
                                + " for rule " + rule.getRuleCode());
            }
            if (rule.getMaxAmount() != null && amount.compareTo(rule.getMaxAmount()) > 0) {
                throw new BusinessRuleViolationException(
                        "AMOUNT_EXCEEDS_LIMIT",
                        "Amount " + amount + " exceeds limit " + rule.getMaxAmount()
                                + " for rule " + rule.getRuleCode());
            }
        }
    }
}
