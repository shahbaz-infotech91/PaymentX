package com.paymentx.prompt.repository;

import com.paymentx.prompt.entity.PromptStatus;
import com.paymentx.prompt.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * English:
 * Spring Data JPA repository for PromptVersion. WHY
 * findByPromptTemplateIdAndStatus returns Optional (a single row, not a
 * List) for an ACTIVE lookup: the database partial unique index on
 * (prompt_template_id) WHERE status='ACTIVE' (see
 * V1_0_0__create_prompt_tables.yaml) guarantees at most one row can
 * ever match - this method's return type documents that guarantee
 * rather than forcing every caller to handle a hypothetical list of
 * "active versions." WHY nextVersionNumber is a real MAX(version_number)
 * query, not "count existing rows + 1": a version can never be deleted
 * in this service (see PromptVersion's javadoc on immutability), so the
 * two are equivalent today, but MAX is the version genuinely correct
 * against any future soft-delete/archival-removal change, and it is
 * the query Postgres can answer directly from the existing
 * (prompt_template_id, version_number) index.
 * Why it exists: the "find version, list versions, find active version"
 * responsibilities Step 7 lists.
 * How it communicates with other components: injected into
 * PromptServiceImpl; the DB-level partial unique index this repository
 * relies on is what Step 20/21's concurrency requirement is actually
 * enforced by, not application code alone.
 *
 * Hinglish:
 * PromptVersion ke liye Spring Data JPA repository. Ek ACTIVE lookup ke
 * liye findByPromptTemplateIdAndStatus Optional (ek single row, List
 * nahi) KYU return karta hai: (prompt_template_id) par database partial
 * unique index WHERE status='ACTIVE' (V1_0_0__create_prompt_tables.yaml
 * dekho) guarantee karta hai ki kabhi zyada se zyada ek hi row match ho
 * sakti hai - is method ka return type us guarantee ko document karta
 * hai, har caller ko "active versions" ki ek hypothetical list handle
 * karne par majboor karne ke bajaye. nextVersionNumber ek real
 * MAX(version_number) query KYU hai, "existing rows count + 1" nahi:
 * is service me ek version kabhi delete nahi ho sakta (PromptVersion ka
 * javadoc immutability par dekho), isliye dono aaj equivalent hain,
 * lekin MAX wo version hai jo kisi bhi future soft-delete/archival-
 * removal change ke against genuinely correct rehta hai, aur ye wahi
 * query hai jo Postgres existing (prompt_template_id, version_number)
 * index se seedhe answer kar sakta hai.
 * Ye kyu hai: Step 7 ki "find version, list versions, find active
 * version" responsibilities.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl
 * me inject hota hai; ye repository jis DB-level partial unique index
 * par depend karta hai wahi Step 20/21 ki concurrency requirement ko
 * actually enforce karta hai, sirf application code nahi.
 */
public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {

    List<PromptVersion> findByPromptTemplateIdOrderByVersionNumberDesc(UUID promptTemplateId);

    Optional<PromptVersion> findByPromptTemplateIdAndVersionNumber(UUID promptTemplateId, Integer versionNumber);

    Optional<PromptVersion> findByPromptTemplateIdAndStatus(UUID promptTemplateId, PromptStatus status);

    boolean existsByPromptTemplateIdAndVersionNumber(UUID promptTemplateId, Integer versionNumber);

    long countByPromptTemplateId(UUID promptTemplateId);

    @Query("select coalesce(max(v.versionNumber), 0) from PromptVersion v where v.promptTemplateId = :promptTemplateId")
    int findMaxVersionNumber(@Param("promptTemplateId") UUID promptTemplateId);
}
