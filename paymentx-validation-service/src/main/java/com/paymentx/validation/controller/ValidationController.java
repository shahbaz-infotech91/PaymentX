package com.paymentx.validation.controller;

import com.paymentx.validation.dto.PaymentValidationRequest;
import com.paymentx.validation.dto.ValidationResponse;
import com.paymentx.validation.service.ValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * WHY the response is 200 OK even for a REJECTED payment, but 409 for a
 * DUPLICATE: this is a genuinely debatable API design choice worth
 * understanding both sides of, since it comes up in interviews.
 *   - REJECTED is a normal, expected business OUTCOME of validation doing
 *     its job correctly - the request itself was well-formed and fully
 *     processed. Returning 200 with a REJECTED status in the body is
 *     consistent with treating "payment rejected" as a valid result, not
 *     an error - the same way a loan-eligibility check returning
 *     "not eligible" isn't an HTTP error.
 *   - DUPLICATE is different: the caller is asking us to do something we
 *     have ALREADY done. 409 Conflict communicates "this exact request
 *     was already handled" at the HTTP semantic level, which lets a
 *     caller distinguish "my payment was rejected" from "I already sent
 *     this" without parsing the response body.
 * A payment gateway (e.g. Stripe) largely follows this same pattern.
 */
@RestController
@RequestMapping("/api/v1/validations")
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationController is a REST controller in the validation module of PaymentX. It lives in package com.paymentx.validation.controller and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationController PaymentX ke validation module ka ek REST controller hai. Ye com.paymentx.validation.controller package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidationController {

    private final ValidationService validationService;

    @PostMapping
    public ResponseEntity<ValidationResponse> validate(
            @Valid @RequestBody PaymentValidationRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String traceId) {

        ValidationResponse response = validationService.validate(request, traceId);

        HttpStatus status = switch (response.status()) {
            case VALIDATED, REJECTED -> HttpStatus.OK;
            case DUPLICATE -> HttpStatus.CONFLICT;
        };

        return ResponseEntity.status(status).body(response);
    }
}
