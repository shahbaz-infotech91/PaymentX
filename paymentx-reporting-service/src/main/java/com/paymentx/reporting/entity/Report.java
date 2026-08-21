package com.paymentx.reporting.entity;

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
 * ====================================================================
 * ENGLISH:
 * The catalog entry for one kind of report - its type, a human-readable
 * name, and a description of what it shows. This is reference/master
 * data: it does NOT hold any generated data itself (ReportExecution and
 * ReportResult do). It exists so the REST API's "list reports" endpoint
 * has something to enumerate, and so a ReportRequest can validate that
 * the requested reportType is actually a real, supported report before
 * kicking off generation.
 *
 * HINGLISH:
 * Ye ek report-type ka catalog entry hai - uska type, ek insaan-padhne-
 * layak naam, aur wo kya dikhata hai uska description. Ye reference/
 * master data hai: isme khud koi generated data nahi hota (wo
 * ReportExecution aur ReportResult me hota hai). Ye isliye hai taaki
 * REST API ke "list reports" endpoint ke paas dikhane ko kuch ho, aur
 * taaki ReportRequest generation shuru karne se pehle check kar sake
 * ki requested reportType actually ek real, supported report hai.
 * ====================================================================
 */
@Entity
@Table(name = "report")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Report extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, unique = true, length = 32)
    private ReportType reportType;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active;
}
