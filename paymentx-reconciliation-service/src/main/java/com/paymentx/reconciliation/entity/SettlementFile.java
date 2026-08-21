package com.paymentx.reconciliation.entity;

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
 * WHY checksumHash exists and is uniquely constrained (see Liquibase
 * changeset): "Duplicate detection" is explicit in the requirements at
 * the FILE level, not just the record level - uploading the exact same
 * settlement file twice (e.g. an operator re-uploading after a network
 * timeout made them think the first upload failed) must be rejected
 * before any of its records are even parsed, not discovered only after
 * duplicate ReconciliationRecord rows exist.
 */
@Entity
@Table(name = "settlement_file")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementFile is a JPA entity in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementFile PaymentX ke reconciliation module ka ek JPA entity hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SettlementFile extends AuditableEntity {

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 16)
    private SettlementFileType fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SettlementFileStatus status;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "checksum_hash", length = 64)
    private String checksumHash;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    /** WHY a disk path, not the raw bytes in a BYTEA column: storing a
     *  potentially 100MB+ settlement file as a Postgres byte[] column
     *  would require fully materializing it in Java memory BOTH at
     *  upload time (file.getBytes()) AND at batch-processing time
     *  (loading the column back) - directly contradicting the
     *  streaming/constant-memory design SettlementFileImporter commits
     *  to for the actual parsing. Writing to disk at upload time and
     *  reading via a plain FileInputStream at batch-processing time
     *  keeps memory usage bounded regardless of file size, while still
     *  solving the original problem (MultipartFile's temp file cannot
     *  survive past the original HTTP request - see
     *  ReconciliationServiceImpl.uploadSettlementFile(), which persists
     *  to THIS path, not Spring's own request-scoped temp file). */
    @Column(name = "storage_path", length = 512)
    private String storagePath;
}
