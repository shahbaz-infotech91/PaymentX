package com.paymentx.prompt.entity;

import com.paymentx.common.base.AuditableEntity;
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

/**
 * English:
 * A named, versioned AI prompt - the stable identity (promptKey, name,
 * type) that PromptVersion rows attach to. WHY no `status` column here
 * (unlike the flattened field list sketched in Step 3 of the Phase 3.2
 * brief): a template's own "is it usable" state is entirely derived
 * from whether it has an ACTIVE PromptVersion (see PromptStatus's
 * javadoc) - a second, independently-settable status field here could
 * drift out of sync with the real version-level truth (e.g. template
 * marked ACTIVE while every version is ARCHIVED), which is exactly the
 * kind of ambiguous state Step 4/20 of the brief says the system must
 * prevent. WHY promptKey is a plain unique VARCHAR, not a separate
 * lookup table: it IS the natural business key every caller (AI Chat
 * Service, future LLM Service) already knows and searches by (e.g.
 * "PAYMENT_ERROR_ANALYSIS") - a surrogate UUID is still the JPA/FK
 * primary key for join stability, matching BaseEntity's existing
 * platform-wide convention (see Routing Service's RoutingRule for the
 * same split).
 * Why it exists: PromptTemplate - Step 2/3 of the Phase 3.2 brief, the
 * "storage" and part of the "auditability" requirement (createdAt/
 * createdBy/updatedAt/updatedBy/version come from AuditableEntity).
 * How it communicates with other components: PromptTemplateRepository
 * persists/queries this; PromptVersion rows reference it by
 * promptTemplateId (a plain FK column, not a JPA @OneToMany/@ManyToOne
 * association - kept simple and avoids lazy-loading surprises, matching
 * RoutingRule's flat-entity style); PromptController never exposes this
 * entity directly, only PromptResponse/PromptVersionResponse.
 *
 * Hinglish:
 * Ek named, versioned AI prompt - wo stable identity (promptKey, name,
 * type) jiske saath PromptVersion rows attach hoti hain. Yahan `status`
 * column KYUN NAHI hai (Phase 3.2 brief ke Step 3 me sketch kiye gaye
 * flattened field list ke ulat): ek template ka apna "kya ye usable
 * hai" state poori tarah is baat se derive hota hai ki uska koi ACTIVE
 * PromptVersion hai ya nahi (PromptStatus ka javadoc dekho) - yahan ek
 * doosra, independently-settable status field real version-level truth
 * se sync se drift ho sakta tha (jaise template ACTIVE marked ho jabki
 * har version ARCHIVED ho), jo exactly wahi ambiguous state hai jise
 * brief ka Step 4/20 rokna mandatory karta hai. promptKey ek plain
 * unique VARCHAR KYU hai, ek alag lookup table nahi: ye wahi natural
 * business key hai jise har caller (AI Chat Service, future LLM
 * Service) already jaanta hai aur jisse search karta hai (jaise
 * "PAYMENT_ERROR_ANALYSIS") - ek surrogate UUID phir bhi join
 * stability ke liye JPA/FK primary key hai, BaseEntity ke existing
 * platform-wide convention se match karte hue (Routing Service ka
 * RoutingRule dekho isi split ke liye).
 * Ye kyu hai: PromptTemplate - Phase 3.2 brief ka Step 2/3, "storage"
 * aur "auditability" requirement ka hissa (createdAt/createdBy/
 * updatedAt/updatedBy/version AuditableEntity se aate hain).
 * Dusre components se kaise communicate karta hai:
 * PromptTemplateRepository ise persist/query karta hai; PromptVersion
 * rows ise promptTemplateId se reference karte hain (ek plain FK
 * column, JPA @OneToMany/@ManyToOne association nahi - simple rakha
 * gaya hai aur lazy-loading surprises se bachta hai, RoutingRule ke
 * flat-entity style se match karte hue); PromptController ye entity
 * kabhi direct expose nahi karta, sirf PromptResponse/
 * PromptVersionResponse.
 */
@Entity
@Table(name = "prompt_template")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate extends AuditableEntity {

    @Column(name = "prompt_key", nullable = false, unique = true, length = 128)
    private String promptKey;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private PromptType type;
}
