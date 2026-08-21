package com.paymentx.controlcenter.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ENGLISH: What the browser submits to POST /api/v1/ai/chat. What it
 * does: carries the user's typed message plus an optional
 * conversationId (client-generated UUID - see frontend
 * utils/aiConversationStore.ts - since no server-side conversation
 * persistence exists yet in Phase 3.1; a real backend-issued
 * conversationId arrives once AI Chat Interface's own storage exists,
 * Phase 3.2+). message is bounded (1-4000 chars) purely as basic input
 * hygiene at the API boundary - this is not a token-accurate LLM
 * context-window limit (LLM Service, Phase 3.3, owns that concern).
 * Why it exists: the one request shape AiChatService/AiController
 * accept - validated with @NotBlank/@Size so a malformed request fails
 * with a real 400 VALIDATION_ERROR (GlobalExceptionHandler) before
 * ever reaching AiChatService, matching how @Valid is already used
 * across real PaymentX business services. How it will communicate with
 * the backend: bound from the request body by
 * AiController.chat(@Valid @RequestBody AiChatRequest).
 *
 * HINGLISH: Browser POST /api/v1/ai/chat par kya submit karta hai. Ye
 * kya karti hai: user ka typed message plus ek optional conversationId
 * carry karti hai (client-generated UUID - frontend
 * utils/aiConversationStore.ts dekho - kyunki Phase 3.1 me abhi koi
 * server-side conversation persistence exist nahi karti; ek real
 * backend-issued conversationId tab aayega jab AI Chat Interface ka
 * apna storage exist karega, Phase 3.2+). message bounded hai (1-4000
 * chars) sirf API boundary par basic input hygiene ke roop me - ye
 * koi token-accurate LLM context-window limit nahi hai (LLM Service,
 * Phase 3.3, us concern ka owner hai). Ye dashboard me kyu hai: ye ek
 * hi request shape hai jise AiChatService/AiController accept karte
 * hain - @NotBlank/@Size se validated, taaki ek malformed request ek
 * real 400 VALIDATION_ERROR ke saath fail ho (GlobalExceptionHandler),
 * AiChatService tak pahunchne se pehle hi, exactly waise jaise @Valid
 * real PaymentX business services me already use hota hai. Backend se
 * kaise connect hogi: AiController.chat(@Valid @RequestBody
 * AiChatRequest) request body se ise bind karta hai.
 */
public record AiChatRequest(
        String conversationId,

        @NotBlank(message = "message must not be blank")
        @Size(min = 1, max = 4000, message = "message must be between 1 and 4000 characters")
        String message
) {
}
