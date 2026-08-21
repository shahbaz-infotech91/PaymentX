package com.paymentx.reporting.repository;

import com.paymentx.reporting.entity.ReportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Spring Data repository for ReportSchedule. findByActiveTrue
 * backs ReportScheduler - it loads every active schedule ONCE per cron
 * tick rather than querying per-schedule, avoiding N+1.
 *
 * HINGLISH: ReportSchedule ke liye Spring Data repository.
 * findByActiveTrue ReportScheduler ko support karta hai - ye har active
 * schedule ko ek hi baar load karta hai per cron tick, N+1 avoid karne
 * ke liye.
 * ====================================================================
 */
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {
    List<ReportSchedule> findByActiveTrue();
}
