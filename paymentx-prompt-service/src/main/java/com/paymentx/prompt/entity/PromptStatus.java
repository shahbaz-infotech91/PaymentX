package com.paymentx.prompt.entity;

/**
 * English:
 * The lifecycle state of one PromptVersion (never of a PromptTemplate -
 * a template's own "is it in use" state is derived from whether any of
 * its versions is ACTIVE, not tracked separately, to avoid two
 * independently-updatable status fields drifting out of sync). DRAFT:
 * created but never activated - safe to edit further before it ever
 * reaches a real caller. ACTIVE: the one version PromptService.render()
 * resolves to when no explicit version is requested - at most one per
 * prompt key, enforced by a database partial unique index (see
 * V1_0_0__create_prompt_tables.yaml), not just application logic.
 * INACTIVE: was ACTIVE, deliberately superseded/deactivated, still
 * fully renderable if explicitly requested by version number (e.g. for
 * audit/reproducibility of a past AI response). ARCHIVED: retired -
 * this service does not currently reject rendering an ARCHIVED version
 * explicitly requested by number, but it can never be (re)activated
 * (see PromptServiceImpl.activateVersion's INVALID_PROMPT_STATUS check).
 * Why it exists: Step 4 of the Phase 3.2 brief - mandatory versioning
 * with an explicit, closed status vocabulary instead of ad-hoc strings.
 * How it communicates with other components: @Enumerated(STRING) column
 * on prompt_version.status; serialized as a plain string in
 * PromptVersionResponse.
 *
 * Hinglish:
 * Ek PromptVersion ka lifecycle state (kabhi ek PromptTemplate ka nahi -
 * ek template ka apna "kya ye use me hai" state uske kisi version ke
 * ACTIVE hone se derive hota hai, alag se track nahi hota, taaki do
 * independently-updatable status fields kabhi sync se drift na hon).
 * DRAFT: create hua lekin kabhi activate nahi hua - kisi real caller
 * tak pahunchne se pehle safely aur edit ho sakta hai. ACTIVE: wo ek
 * version jise PromptService.render() resolve karta hai jab koi
 * explicit version request nahi kiya gaya - har prompt key ke liye
 * zyada se zyada ek, ek database partial unique index se enforce hota
 * hai (V1_0_0__create_prompt_tables.yaml dekho), sirf application logic
 * se nahi. INACTIVE: pehle ACTIVE tha, jaan-boojh kar supersede/
 * deactivate kiya gaya, phir bhi fully renderable hai agar explicitly
 * version number se request kiya jaaye (jaise ek past AI response ke
 * audit/reproducibility ke liye). ARCHIVED: retire ho chuka - ye
 * service currently ek explicitly number se requested ARCHIVED version
 * ko render karne se reject nahi karti, lekin ise kabhi (re)activate
 * nahi kiya ja sakta (PromptServiceImpl.activateVersion ka
 * INVALID_PROMPT_STATUS check dekho).
 * Ye kyu hai: Phase 3.2 brief ka Step 4 - mandatory versioning, ek
 * explicit, closed status vocabulary ke saath, ad-hoc strings ke
 * bajaye.
 * Dusre components se kaise communicate karta hai: prompt_version.status
 * par @Enumerated(STRING) column; PromptVersionResponse me ek plain
 * string ke roop me serialize hota hai.
 */
public enum PromptStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
