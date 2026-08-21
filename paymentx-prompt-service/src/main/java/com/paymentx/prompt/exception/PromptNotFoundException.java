package com.paymentx.prompt.exception;

import com.paymentx.common.exception.PaymentXException;

/**
 * English:
 * "The prompt/version you asked for by key or version number does not
 * exist" - used for PROMPT_NOT_FOUND, PROMPT_VERSION_NOT_FOUND, and
 * NO_ACTIVE_VERSION (see PromptErrorCodes). Extends PaymentXException
 * directly rather than common-library's ResourceNotFoundException
 * because that class hardcodes errorCode="RESOURCE_NOT_FOUND" (see its
 * javadoc) - Step 19 of the Phase 3.2 brief requires the three distinct
 * codes above, not one generic code with three different messages.
 * Why it exists: one small, reusable exception type instead of three
 * near-identical subclasses (PromptNotFoundException/
 * PromptVersionNotFoundException/NoActiveVersionException) - the
 * errorCode constructor parameter already carries the distinction Step
 * 19 needs, a class hierarchy would add nothing but boilerplate.
 * How it communicates with other components: caught by
 * GlobalExceptionHandler.handlePromptNotFound, mapped to HTTP 404.
 *
 * Hinglish:
 * "Jo prompt/version aapne key ya version number se maanga wo exist
 * nahi karta" - PROMPT_NOT_FOUND, PROMPT_VERSION_NOT_FOUND, aur
 * NO_ACTIVE_VERSION ke liye use hota hai (PromptErrorCodes dekho).
 * common-library ke ResourceNotFoundException ke bajaye seedhe
 * PaymentXException extend karta hai kyunki wo class errorCode=
 * "RESOURCE_NOT_FOUND" hardcode karti hai (uska javadoc dekho) - Phase
 * 3.2 brief ka Step 19 upar wale teen distinct codes maangta hai, teen
 * alag messages ke saath ek generic code nahi.
 * Ye kyu hai: teen near-identical subclasses
 * (PromptNotFoundException/PromptVersionNotFoundException/
 * NoActiveVersionException) ke bajaye ek chhota, reusable exception
 * type - errorCode constructor parameter already wo distinction carry
 * karta hai jo Step 19 ko chahiye, ek class hierarchy sirf boilerplate
 * add karti.
 * Dusre components se kaise communicate karta hai:
 * GlobalExceptionHandler.handlePromptNotFound ise catch karta hai, HTTP
 * 404 par map karta hai.
 */
public class PromptNotFoundException extends PaymentXException {

    public PromptNotFoundException(String errorCode, String message) {
        super(errorCode, message, false);
    }
}
