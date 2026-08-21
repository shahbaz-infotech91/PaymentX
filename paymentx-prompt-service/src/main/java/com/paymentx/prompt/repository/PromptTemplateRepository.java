package com.paymentx.prompt.repository;

import com.paymentx.prompt.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * English:
 * Spring Data JPA repository for PromptTemplate - plain
 * JpaRepository&lt;PromptTemplate, UUID&gt;, the same persistence
 * approach every existing PaymentX service already uses (Step 7 of the
 * Phase 3.2 brief: "use Spring Data/JPA only if consistent with
 * existing PaymentX. Do NOT introduce another persistence framework" -
 * this introduces nothing new). findByPromptKey/existsByPromptKey are
 * derived queries backed by the unique index on prompt_template.
 * prompt_key (see V1_0_0__create_prompt_tables.yaml).
 * Why it exists: the "find by prompt key" responsibility Step 7 lists.
 * How it communicates with other components: injected into
 * PromptServiceImpl; every read/write of a PromptTemplate row goes
 * through here.
 *
 * Hinglish:
 * PromptTemplate ke liye Spring Data JPA repository - plain
 * JpaRepository&lt;PromptTemplate, UUID&gt;, wahi persistence approach
 * jo har existing PaymentX service already use karti hai (Phase 3.2
 * brief ka Step 7: "Spring Data/JPA sirf tab use karo jab existing
 * PaymentX se consistent ho. Koi doosra persistence framework introduce
 * mat karo" - ye kuch bhi naya introduce nahi karta). findByPromptKey/
 * existsByPromptKey derived queries hain jo prompt_template.prompt_key
 * ke unique index se backed hain (V1_0_0__create_prompt_tables.yaml
 * dekho).
 * Ye kyu hai: Step 7 ki "find by prompt key" responsibility.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl me
 * inject hota hai; ek PromptTemplate row ka har read/write yahin se
 * guzarta hai.
 */
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, UUID> {

    Optional<PromptTemplate> findByPromptKey(String promptKey);

    boolean existsByPromptKey(String promptKey);
}
