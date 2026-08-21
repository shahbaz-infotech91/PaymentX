package com.paymentx.reconciliation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * ENGLISH: Published (never handled directly) whenever a reconciliation
 * batch row has just been inserted and needs its async processing kicked
 * off. WHY this indirection instead of just calling
 * reconciliationBatchProcessor.runAsync(batchId) directly from
 * ReconciliationServiceImpl: that direct call fires @Async immediately,
 * often before the CALLING method's own @Transactional commit has
 * happened - the async thread then runs on a separate DB connection and,
 * under READ COMMITTED isolation, cannot see the just-inserted (still
 * uncommitted) batch row, failing with "Batch not found at execution
 * time". Routing through a plain (non-transactional) ApplicationEvent and
 * an AFTER_COMMIT @TransactionalEventListener (see
 * ReconciliationEventListener) guarantees the async work only starts once
 * the row is genuinely visible to other connections. This mirrors the
 * exact pattern this codebase already uses for
 * BatchCompletionApplicationEvent/MismatchDetectionApplicationEvent/
 * SettlementCompletionApplicationEvent - found and fixed during Phase 1
 * validation, where a real end-to-end reconciliation run surfaced this as
 * a genuine race, not a hypothetical one.
 *
 * HINGLISH: Ye event tab publish hota hai jab ek reconciliation batch row
 * database me insert ho chuka hai aur uska async processing start karna
 * hai. Seedha reconciliationBatchProcessor.runAsync(batchId) call karne
 * ki jagah ye event route isliye use hota hai kyunki @Async turant fire
 * ho jata hai - kabhi kabhi calling method ka @Transactional commit hone
 * se pehle hi. Us waqt async thread ek alag DB connection par chalta hai
 * aur READ COMMITTED isolation ke wajah se abhi-abhi insert hui (lekin
 * commit na hui) row ko dekh nahi pata, jisse "Batch not found at
 * execution time" jaisi real error aati hai. AFTER_COMMIT
 * @TransactionalEventListener (dekhiye ReconciliationEventListener) ke
 * through route karne se guarantee milta hai ki async kaam tabhi shuru
 * ho jab row sach me commit ho chuki ho aur dusre connections ko dikhti
 * ho.
 */
@Getter
public class BatchStartRequestedApplicationEvent extends ApplicationEvent {
    private final UUID batchId;

    public BatchStartRequestedApplicationEvent(Object source, UUID batchId) {
        super(source);
        this.batchId = batchId;
    }
}
