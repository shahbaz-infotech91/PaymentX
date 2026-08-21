package com.paymentx.prompt.service;

import com.paymentx.prompt.dto.CreatePromptRequest;
import com.paymentx.prompt.dto.CreatePromptVersionRequest;
import com.paymentx.prompt.dto.PromptResponse;
import com.paymentx.prompt.dto.PromptVersionResponse;
import com.paymentx.prompt.dto.RenderPromptRequest;
import com.paymentx.prompt.dto.RenderPromptResponse;

import java.util.List;

/**
 * English:
 * The business-logic contract for prompt management - what
 * PromptController delegates to, and the seam a future consumer
 * (LLM Service, Phase 3.3) would call through directly if it ever runs
 * in-process rather than over HTTP. Interface + impl split (matching
 * Routing Service's RoutingService/RoutingServiceImpl) rather than one
 * concrete class - lets a test double substitute for the whole service
 * layer without needing to construct a real PromptServiceImpl.
 * Why it exists: Step 8 of the Phase 3.2 brief's exact operation list -
 * create prompt, create version, get prompt, get active version,
 * activate/deactivate version, render, with variable validation as a
 * property of render rather than a separate public operation.
 * How it communicates with other components: implemented by
 * PromptServiceImpl; called by PromptController.
 *
 * Hinglish:
 * Prompt management ke liye business-logic contract - jise
 * PromptController delegate karta hai, aur wo seam jise ek future
 * consumer (LLM Service, Phase 3.3) seedhe call karega agar ye kabhi
 * HTTP ke bajaye in-process chale. Interface + impl split (Routing
 * Service ke RoutingService/RoutingServiceImpl se match karte hue) ek
 * concrete class ke bajaye - ek test double ko poore service layer ke
 * liye substitute karne deta hai bina ek real PromptServiceImpl
 * construct kiye.
 * Ye kyu hai: Phase 3.2 brief ke Step 8 ki exact operation list - create
 * prompt, create version, get prompt, get active version, activate/
 * deactivate version, render, variable validation render ki ek property
 * ke roop me, ek alag public operation ke bajaye.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl
 * ise implement karta hai; PromptController ise call karta hai.
 */
public interface PromptService {

    PromptResponse createPrompt(CreatePromptRequest request);

    PromptResponse getPrompt(String promptKey);

    List<PromptVersionResponse> listVersions(String promptKey);

    PromptVersionResponse getVersion(String promptKey, int versionNumber);

    PromptVersionResponse getActiveVersion(String promptKey);

    PromptVersionResponse createVersion(String promptKey, CreatePromptVersionRequest request);

    PromptVersionResponse activateVersion(String promptKey, int versionNumber);

    PromptVersionResponse deactivateVersion(String promptKey, int versionNumber);

    RenderPromptResponse renderPrompt(String promptKey, RenderPromptRequest request);
}
