package com.paymentx.common.exception;

/**
 * "The thing you asked for by ID/reference does not exist" - a
 * universal REST concern (not in the newest message's explicit minimum
 * list, but present in the original spec and genuinely cross-cutting in
 * the same way Conflict/Unauthorized/Forbidden are; included for
 * completeness of the standard HTTP-semantic exception set).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ResourceNotFoundException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ResourceNotFoundException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ResourceNotFoundException extends PaymentXException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND", resourceType + " not found: " + identifier, false);
    }
}
