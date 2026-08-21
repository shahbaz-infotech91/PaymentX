package com.paymentx.prompt.entity;

import com.paymentx.common.base.AuditableEntity;
import com.paymentx.prompt.dto.PromptVariable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * English:
 * One immutable-in-content version of a PromptTemplate's rendered
 * text, plus the variable declarations that text is allowed to
 * reference. WHY `variables` is a native Postgres JSON column
 * (@JdbcTypeCode(SqlTypes.JSON), Hibernate ORM 6's built-in JSON
 * mapping - no extra persistence-framework dependency added, per Step
 * 7 of the Phase 3.2 brief) rather than a separate prompt_variable
 * table: a version's variables have no independent lifecycle - they
 * are never queried, updated, or deleted except as part of their
 * owning version, and are always read together with it (see
 * PromptVariable's javadoc for the full reasoning) - Step 5 explicitly
 * permits this simplification when the design is genuinely safe.
 * WHY content is never mutated after creation for a given (templateId,
 * versionNumber): editing a live/previously-rendered version's wording
 * silently would break reproducibility (a past AI answer could no
 * longer be explained by re-rendering the version that produced it) -
 * a content change is a NEW version, full stop; PromptServiceImpl has
 * no "update version content" operation.
 * Why it exists: PromptVersion - Step 2/4 of the Phase 3.2 brief:
 * versioning, activation-state tracking (status), and the data
 * PromptRenderer validates against.
 * How it communicates with other components: PromptVersionRepository
 * persists/queries this (including the at-most-one-ACTIVE-per-template
 * enforcement - see V1_0_0__create_prompt_tables.yaml's partial unique
 * index); PromptServiceImpl.renderPrompt reads content+variables from
 * whichever row it resolves (explicit version, or the ACTIVE one) and
 * hands both to PromptRenderer.
 *
 * Hinglish:
 * Ek PromptTemplate ka ek content-immutable version, plus wo variable
 * declarations jinhe wo text reference kar sakta hai. `variables` ek
 * native Postgres JSON column (@JdbcTypeCode(SqlTypes.JSON), Hibernate
 * ORM 6 ka built-in JSON mapping - koi extra persistence-framework
 * dependency add nahi ki gayi, Phase 3.2 brief ke Step 7 ke hisaab se)
 * KYU hai, ek alag prompt_variable table ke bajaye: ek version ke
 * variables ki koi independent lifecycle nahi hoti - wo kabhi apni
 * owning version ke hisse ke alawa query, update, ya delete nahi hote,
 * aur hamesha usi ke saath padhe jaate hain (PromptVariable ka javadoc
 * dekho poori reasoning ke liye) - Step 5 explicitly is simplification
 * ki ijazat deta hai jab design genuinely safe ho. Content ek diye
 * hue (templateId, versionNumber) ke liye creation ke baad kabhi
 * mutate KYU nahi hota: ek live/pehle-render-hui version ki wording
 * silently edit karna reproducibility todta (ek past AI answer ab us
 * version ko dobara render karke explain nahi ho sakta jisne use
 * produce kiya tha) - ek content change ek NAYA version hai, bas;
 * PromptServiceImpl ke paas koi "update version content" operation
 * nahi hai.
 * Ye kyu hai: PromptVersion - Phase 3.2 brief ka Step 2/4: versioning,
 * activation-state tracking (status), aur wo data jise PromptRenderer
 * validate karta hai.
 * Dusre components se kaise communicate karta hai:
 * PromptVersionRepository ise persist/query karta hai (including
 * at-most-one-ACTIVE-per-template enforcement -
 * V1_0_0__create_prompt_tables.yaml ka partial unique index dekho);
 * PromptServiceImpl.renderPrompt jo bhi row resolve karta hai (explicit
 * version, ya ACTIVE wala) usse content+variables padhta hai aur dono
 * PromptRenderer ko deta hai.
 */
@Entity
@Table(name = "prompt_version")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVersion extends AuditableEntity {

    @Column(name = "prompt_template_id", nullable = false)
    private UUID promptTemplateId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PromptStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", nullable = false, columnDefinition = "jsonb")
    private List<PromptVariable> variables;
}
