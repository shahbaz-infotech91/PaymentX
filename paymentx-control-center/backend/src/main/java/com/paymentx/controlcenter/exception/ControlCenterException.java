package com.paymentx.controlcenter.exception;

import lombok.Getter;

/**
 * ENGLISH: The base checked-at-runtime exception for this module. What
 * it does: carries a stable errorCode alongside the message, so
 * GlobalExceptionHandler doesn't need to string-match messages to build
 * ErrorResponse.errorCode. Why it exists: Phase 2 service-client code
 * (calling the real PaymentX services/infrastructure) will throw
 * subclasses of this for real failure conditions (service unreachable,
 * timeout, etc.) - defined now, in Phase 1, so the exception-handling
 * contract exists before any real caller needs it. How it will
 * communicate with the backend: caught by GlobalExceptionHandler,
 * turned into an ApiResponse.error(...) the frontend can branch on by
 * errorCode.
 *
 * HINGLISH: Ye is module ka base runtime exception hai. Ye kya karti
 * hai: message ke saath ek stable errorCode carry karti hai, taaki
 * GlobalExceptionHandler ko ErrorResponse.errorCode banane ke liye
 * message string-match na karna pade. Ye dashboard me kyu hai: Phase 2
 * ka service-client code (real PaymentX services/infrastructure ko call
 * karne wala) real failure conditions (service unreachable, timeout,
 * etc.) ke liye iski subclasses throw karega - abhi, Phase 1 me hi,
 * define kar diya gaya hai taaki exception-handling contract kisi bhi
 * real caller ki zaroorat se pehle maujood ho. Backend se kaise connect
 * hogi: GlobalExceptionHandler ise catch karta hai, ek
 * ApiResponse.error(...) me convert karta hai jise frontend errorCode
 * ke basis par branch kar sakta hai.
 */
@Getter
public class ControlCenterException extends RuntimeException {

    private final String errorCode;

    public ControlCenterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ControlCenterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
