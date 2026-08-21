package com.paymentx.prompt.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.prompt.dto.CreatePromptRequest;
import com.paymentx.prompt.dto.CreatePromptVersionRequest;
import com.paymentx.prompt.dto.PromptResponse;
import com.paymentx.prompt.dto.PromptVersionResponse;
import com.paymentx.prompt.dto.RenderPromptRequest;
import com.paymentx.prompt.dto.RenderPromptResponse;
import com.paymentx.prompt.service.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * English:
 * The Phase 3.2 Prompt Service API surface - following the exact
 * `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope + @PreAuthorize
 * ("hasRole(...)") admin-gated-writes/open-reads convention Routing
 * Service's RoutingController already establishes (Step 11 of the
 * Phase 3.2 brief: "use the exact conventions discovered in the
 * existing repository"). WHY render() is open (no @PreAuthorize) while
 * every other mutating-adjacent endpoint requires PROMPT_ADMIN: render
 * is the one operation a non-admin service-to-service caller (AI Chat
 * Service today; LLM Service, RAG Service, Agent Orchestrator once they
 * exist, per PAYMENTX_PHASE_3_ARCHITECTURE.md §4) genuinely needs to
 * invoke as a routine part of answering a request - gating it behind an
 * admin role would make Prompt Service unusable by its actual intended
 * consumers. WHY GET .../versions/active is its own endpoint rather
 * than folding it into GET .../versions/{version}: "active" is not a
 * version number, and a caller resolving "what should I render right
 * now" should not have to fetch the whole prompt (GET
 * /prompts/{promptKey}) first just to read its activeVersion field.
 * Why it exists: Step 11's endpoint list, adapted to this service's own
 * actual read-resource shape (versions/active mirrors Routing Service's
 * /routes/default sub-resource pattern).
 * How it communicates with other components: this IS the backend
 * endpoint - called today by nothing in production (AI Chat Service's
 * AiChatService.java in paymentx-control-center/backend still throws
 * AI_SERVICE_NOT_READY, unchanged by this phase - see
 * PAYMENTX_PHASE_3_2_PROMPT_SERVICE.md §19); wired up for real once
 * Phase 3.3's LLM Service (or an updated AI Chat Service) calls it
 * through a new API Gateway route.
 *
 * Hinglish:
 * Phase 3.2 ka Prompt Service API surface - exactly wahi
 * `/api/v1/{resource}` + ApiResponse&lt;T&gt; envelope +
 * @PreAuthorize("hasRole(...)") admin-gated-writes/open-reads
 * convention follow karte hue jo Routing Service ka RoutingController
 * already establish karta hai (Phase 3.2 brief ka Step 11: "existing
 * repository me discovered exact conventions use karo"). render()
 * open (@PreAuthorize nahi) KYU hai jabki har doosra mutating-adjacent
 * endpoint PROMPT_ADMIN maangta hai: render wo ek operation hai jise ek
 * non-admin service-to-service caller (aaj AI Chat Service; LLM
 * Service, RAG Service, Agent Orchestrator ek baar exist karne par,
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §4 ke hisaab se) ek request ka
 * jawab dete waqt genuinely ek routine part ke roop me invoke karna
 * hota hai - ise ek admin role ke peeche gate karna Prompt Service ko
 * uske actual intended consumers ke liye unusable bana deta. GET
 * .../versions/active apna ek alag endpoint KYU hai, GET
 * .../versions/{version} me fold karne ke bajaye: "active" ek version
 * number nahi hai, aur ek caller jo "abhi mujhe kya render karna
 * chahiye" resolve kar raha hai use pehle poora prompt (GET
 * /prompts/{promptKey}) fetch nahi karna chahiye sirf uska
 * activeVersion field padhne ke liye.
 * Ye kyu hai: Step 11 ki endpoint list, is service ke apne actual
 * read-resource shape ke hisaab se adapt ki gayi (versions/active
 * Routing Service ke /routes/default sub-resource pattern ko mirror
 * karta hai).
 * Dusre components se kaise communicate karta hai: yehi backend
 * endpoint hai - aaj production me kuch bhi ise call nahi karta (AI
 * Chat Service ka AiChatService.java, paymentx-control-center/backend
 * me, abhi bhi AI_SERVICE_NOT_READY throw karta hai, is phase se
 * unchanged - PAYMENTX_PHASE_3_2_PROMPT_SERVICE.md §19 dekho); ek baar
 * Phase 3.3 ka LLM Service (ya ek updated AI Chat Service) ek naye API
 * Gateway route ke through ise call karega, tab ye real me wired up
 * hoga.
 */
@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompts", description = "Prompt template management, versioning, and safe rendering")
public class PromptController {

    private final PromptService promptService;

    @PostMapping
    @PreAuthorize("hasRole('PROMPT_ADMIN')")
    @Operation(summary = "Create a new prompt template with its first (DRAFT) version (admin only)")
    public ResponseEntity<ApiResponse<PromptResponse>> create(@Valid @RequestBody CreatePromptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(promptService.createPrompt(request)));
    }

    @GetMapping("/{promptKey}")
    @Operation(summary = "Get a prompt template, its active version summary, and version count")
    public ResponseEntity<ApiResponse<PromptResponse>> getPrompt(@PathVariable String promptKey) {
        return ResponseEntity.ok(ApiResponse.success(promptService.getPrompt(promptKey)));
    }

    @GetMapping("/{promptKey}/versions")
    @Operation(summary = "List every version of a prompt, newest first")
    public ResponseEntity<ApiResponse<List<PromptVersionResponse>>> listVersions(@PathVariable String promptKey) {
        return ResponseEntity.ok(ApiResponse.success(promptService.listVersions(promptKey)));
    }

    @GetMapping("/{promptKey}/versions/active")
    @Operation(summary = "Get the currently ACTIVE version of a prompt")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> getActiveVersion(@PathVariable String promptKey) {
        return ResponseEntity.ok(ApiResponse.success(promptService.getActiveVersion(promptKey)));
    }

    @GetMapping("/{promptKey}/versions/{version}")
    @Operation(summary = "Get one specific version of a prompt by version number")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> getVersion(@PathVariable String promptKey, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.success(promptService.getVersion(promptKey, version)));
    }

    @PostMapping("/{promptKey}/versions")
    @PreAuthorize("hasRole('PROMPT_ADMIN')")
    @Operation(summary = "Create a new DRAFT version of an existing prompt (admin only)")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> createVersion(
            @PathVariable String promptKey, @Valid @RequestBody CreatePromptVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(promptService.createVersion(promptKey, request)));
    }

    @PostMapping("/{promptKey}/versions/{version}/activate")
    @PreAuthorize("hasRole('PROMPT_ADMIN')")
    @Operation(summary = "Activate a version, atomically deactivating whichever version was previously ACTIVE (admin only)")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> activateVersion(@PathVariable String promptKey, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.success(promptService.activateVersion(promptKey, version)));
    }

    @PostMapping("/{promptKey}/versions/{version}/deactivate")
    @PreAuthorize("hasRole('PROMPT_ADMIN')")
    @Operation(summary = "Deactivate the currently ACTIVE version (admin only)")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> deactivateVersion(@PathVariable String promptKey, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.success(promptService.deactivateVersion(promptKey, version)));
    }

    @PostMapping("/{promptKey}/render")
    @Operation(summary = "Render a prompt version's content with variables substituted - open to any caller reaching this service")
    public ResponseEntity<ApiResponse<RenderPromptResponse>> render(
            @PathVariable String promptKey, @Valid @RequestBody RenderPromptRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promptService.renderPrompt(promptKey, request)));
    }
}
