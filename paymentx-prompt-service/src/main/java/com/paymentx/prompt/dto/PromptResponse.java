package com.paymentx.prompt.dto;

import com.paymentx.prompt.entity.PromptType;

import java.time.OffsetDateTime;

/**
 * English:
 * The wire shape of a PromptTemplate - its identity plus a summary of
 * its current activation state (activeVersion is null when no version
 * has ever been activated, versionCount reflects how many versions
 * exist in total). Deliberately does NOT embed the full list of
 * versions - GET /api/v1/prompts/{promptKey}/versions is the dedicated
 * endpoint for that (avoids an unbounded response body for a
 * heavily-versioned prompt, same "don't return everything from one
 * endpoint" instinct as PageResponse elsewhere in this platform).
 * Why it exists: response shape for POST /api/v1/prompts and GET
 * /api/v1/prompts/{promptKey} - Step 3/11 of the Phase 3.2 brief.
 * How it communicates with other components: built by PromptMapper +
 * PromptServiceImpl (activeVersion/versionCount require a repository
 * lookup beyond the template row itself, so this is assembled in the
 * service layer, not a pure MapStruct mapping); returned inside
 * ApiResponse&lt;PromptResponse&gt; by PromptController.
 *
 * Hinglish:
 * Ek PromptTemplate ka wire shape - uski identity plus uske current
 * activation state ka summary (activeVersion null hota hai jab kabhi
 * koi version activate hi nahi hua, versionCount batata hai total kitne
 * versions exist karte hain). Jaan-boojh kar versions ki poori list
 * embed nahi karta - GET /api/v1/prompts/{promptKey}/versions uske
 * liye dedicated endpoint hai (ek heavily-versioned prompt ke liye ek
 * unbounded response body se bachata hai, wahi "ek endpoint se sab kuch
 * mat return karo" instinct jo is platform me PageResponse kahin aur
 * follow karta hai).
 * Ye kyu hai: POST /api/v1/prompts aur GET /api/v1/prompts/{promptKey}
 * ke liye response shape - Phase 3.2 brief ka Step 3/11.
 * Dusre components se kaise communicate karta hai: PromptMapper +
 * PromptServiceImpl ise banate hain (activeVersion/versionCount ko
 * template row se aage ek repository lookup chahiye, isliye ye service
 * layer me assemble hota hai, ek pure MapStruct mapping nahi); PromptController
 * ise ApiResponse&lt;PromptResponse&gt; ke andar return karta hai.
 */
public record PromptResponse(
        String promptKey,
        String name,
        String description,
        PromptType type,
        int versionCount,
        PromptVersionResponse activeVersion,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy
) {
}
