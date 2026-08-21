package com.paymentx.controlcenter.dto;

/**
 * ENGLISH: The error payload carried inside ApiResponse when a request
 * fails. What it does: gives the frontend a stable errorCode (for
 * programmatic branching) plus a human-readable message and the request
 * path that failed. Why it exists: GlobalExceptionHandler needs one
 * consistent shape to populate regardless of which exception type was
 * thrown. How it will communicate with the backend: produced by
 * GlobalExceptionHandler, consumed by the React api/ layer's error
 * handling.
 *
 * HINGLISH: Ye ApiResponse ke andar carry hone wala error payload hai,
 * jab koi request fail hoti hai. Ye kya karti hai: frontend ko ek stable
 * errorCode (programmatic branching ke liye) plus ek human-readable
 * message aur jo request path fail hua uska path deti hai. Ye dashboard
 * me kyu hai: GlobalExceptionHandler ko ek consistent shape chahiye jo
 * chahe koi bhi exception type throw ho, wahi shape use ho. Backend se
 * kaise connect hogi: GlobalExceptionHandler isse produce karta hai,
 * React ka api/ layer apni error handling me isse consume karta hai.
 */
public record ErrorResponse(
        String errorCode,
        String message,
        String path
) {
}
