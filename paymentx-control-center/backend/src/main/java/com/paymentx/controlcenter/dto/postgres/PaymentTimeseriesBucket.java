package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real time bucket of paymentx_payment.payment activity -
 * a real `date_trunc`'d window, with real total/successful/failed
 * counts computed by one real GROUP BY query (Phase 4 Metrics "
 * Payments/minute" + "Success Rate"/"Failure Rate" charts - there is
 * no Prometheus counter for business payment volume, so this is
 * derived directly from Postgres, the same real source Home page's
 * PaymentStatsSummary uses). An empty bucket (0/0/0) for a real window
 * with no activity is an honest result, never skipped or backfilled
 * with invented data.
 *
 * HINGLISH: paymentx_payment.payment activity ka ek real time bucket -
 * ek real `date_trunc`'d window, real total/successful/failed counts
 * ke saath ek real GROUP BY query se compute kiya gaya (Phase 4
 * Metrics "Payments/minute" + "Success Rate"/"Failure Rate" charts -
 * business payment volume ke liye koi Prometheus counter nahi hai,
 * isliye ye directly Postgres se derive kiya gaya hai, wahi real
 * source jo Home page ka PaymentStatsSummary use karta hai). Ek real
 * window ke liye ek empty bucket (0/0/0) jisme koi activity na ho, ek
 * honest result hai, kabhi skip ya invented data se backfill nahi kiya
 * jaata.
 */
public record PaymentTimeseriesBucket(
        OffsetDateTime bucketStart,
        long total,
        long successful,
        long failed
) {
}
