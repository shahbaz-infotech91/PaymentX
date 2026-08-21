package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.SourceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * Aggregation queries over SourceEvent - every one of these is a real
 * SQL GROUP BY/COUNT/SUM executed IN the database, not fetched-then-
 * summed in Java (which is exactly the N+1 / "fetch everything into
 * memory" anti-pattern the explicit "Efficient SQL, No N+1" requirement
 * forbids). Each query is scoped by sourceService + date range so a
 * specific report generator only aggregates the events relevant to it.
 *
 * HINGLISH:
 * SourceEvent pe aggregation queries - inme se har ek asli SQL GROUP
 * BY/COUNT/SUM hai jo database ke ANDAR chalti hai, Java me fetch-karke-
 * phir-sum nahi karti. Har query sourceService + date range se scoped
 * hai taaki ek specific report generator sirf apne relevant events hi
 * aggregate kare.
 * ====================================================================
 */
public interface SourceEventRepository extends JpaRepository<SourceEvent, UUID> {

    @Query("SELECT s.status AS status, COUNT(s) AS count, COALESCE(SUM(s.amount), 0) AS totalAmount "
            + "FROM SourceEvent s WHERE s.sourceService = :sourceService "
            + "AND s.occurredAt BETWEEN :from AND :to "
            + "GROUP BY s.status")
    List<StatusAggregate> aggregateByStatus(@Param("sourceService") String sourceService,
                                             @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT s.currency AS currency, COUNT(s) AS count, COALESCE(SUM(s.amount), 0) AS totalAmount "
            + "FROM SourceEvent s WHERE s.sourceService = :sourceService "
            + "AND s.occurredAt BETWEEN :from AND :to "
            + "GROUP BY s.currency")
    List<CurrencyAggregate> aggregateByCurrency(@Param("sourceService") String sourceService,
                                                 @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT s.participantId AS participantId, COUNT(s) AS count, COALESCE(SUM(s.amount), 0) AS totalAmount "
            + "FROM SourceEvent s WHERE s.sourceService = :sourceService "
            + "AND s.occurredAt BETWEEN :from AND :to "
            + "GROUP BY s.participantId")
    List<ParticipantAggregate> aggregateByParticipant(@Param("sourceService") String sourceService,
                                                       @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(s) AS count, COALESCE(SUM(s.amount), 0) AS totalAmount "
            + "FROM SourceEvent s WHERE s.sourceService = :sourceService "
            + "AND s.occurredAt BETWEEN :from AND :to")
    TotalAggregate aggregateTotal(@Param("sourceService") String sourceService,
                                   @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** Projection interfaces - Spring Data JPA maps the query result
     *  columns onto these directly, no manual row-mapping code needed. */
    interface StatusAggregate {
        String getStatus();
        long getCount();
        java.math.BigDecimal getTotalAmount();
    }

    interface CurrencyAggregate {
        String getCurrency();
        long getCount();
        java.math.BigDecimal getTotalAmount();
    }

    interface ParticipantAggregate {
        String getParticipantId();
        long getCount();
        java.math.BigDecimal getTotalAmount();
    }

    interface TotalAggregate {
        long getCount();
        java.math.BigDecimal getTotalAmount();
    }
}
