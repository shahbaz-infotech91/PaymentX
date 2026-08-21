package com.paymentx.controlcenter.dto.e2e;

/**
 * ENGLISH: The Phase 5 brief's exact 5-state vocabulary for one E2E
 * stage - PENDING (not reached yet), RUNNING (in flight right now),
 * SUCCESS (a real, positive signal was found), FAILED (a real,
 * negative signal was found), TIMEOUT (the whole run's bounded
 * deadline was hit while this stage was still unresolved). A stage
 * this platform genuinely cannot track per-payment (e.g.
 * Reconciliation/Reporting when this run never triggered them) is
 * reported as PENDING with an honest reason in E2EStage.detail -
 * never silently upgraded to SUCCESS.
 *
 * HINGLISH: Ek E2E stage ke liye Phase 5 brief ka exact 5-state
 * vocabulary - PENDING (abhi tak nahi pahunchi), RUNNING (abhi in
 * flight), SUCCESS (ek real, positive signal mila), FAILED (ek real,
 * negative signal mila), TIMEOUT (poore run ki bounded deadline hit ho
 * gayi jab ye stage abhi bhi unresolved thi). Ek stage jise ye
 * platform genuinely per-payment track nahi kar sakta (jaise
 * Reconciliation/Reporting jab is run ne unhe kabhi trigger hi nahi
 * kiya) PENDING report hoti hai E2EStage.detail me ek honest reason ke
 * saath - kabhi silently SUCCESS tak upgrade nahi ki jaati.
 */
public enum E2EStageStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT
}
