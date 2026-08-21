package com.paymentx.reporting.entity;

/**
 * ====================================================================
 * ENGLISH:
 * How often a scheduled report re-runs. ON_DEMAND represents a manual,
 * one-off request (not tied to a recurring ReportSchedule at all) -
 * included here so ReportRequest/ReportExecution can record "why did
 * this run happen" uniformly, whether triggered by a human or by
 * ReportScheduler's cron.
 *
 * HINGLISH:
 * Ye batata hai ki ek scheduled report kitni baar dobara chalta hai.
 * ON_DEMAND ka matlab hai manual, ek-baar wali request (koi recurring
 * schedule se juda nahi hai) - ye isliye rakha hai taaki
 * ReportRequest/ReportExecution consistently record kar sake ki "ye
 * run kyu hua" - chahe insaan ne manually chalaya ho ya
 * ReportScheduler ke cron ne.
 * ====================================================================
 */
public enum ReportFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    ON_DEMAND
}
