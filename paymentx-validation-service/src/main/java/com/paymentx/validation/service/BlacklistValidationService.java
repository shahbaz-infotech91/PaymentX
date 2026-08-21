package com.paymentx.validation.service;

import com.paymentx.validation.exception.BusinessRuleViolationException;
import com.paymentx.validation.repository.BlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BlacklistValidationService is a service in the validation module of PaymentX. It lives in package com.paymentx.validation.service and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BlacklistValidationService PaymentX ke validation module ka ek service hai. Ye com.paymentx.validation.service package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class BlacklistValidationService {

    private final BlacklistRepository blacklistRepository;

    public void checkNotBlacklisted(String accountNumber, String bankId, String role) {
        blacklistRepository.findByAccountNumberAndBankId(accountNumber, bankId)
                .ifPresent(entry -> {
                    throw new BusinessRuleViolationException(
                            "ACCOUNT_BLACKLISTED_" + role,
                            role + " account '" + accountNumber + "' at bank '" + bankId
                                    + "' is blacklisted: " + entry.getReason());
                });
    }
}
