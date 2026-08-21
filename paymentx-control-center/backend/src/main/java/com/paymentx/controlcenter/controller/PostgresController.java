package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.PageResponse;
import com.paymentx.controlcenter.dto.postgres.*;
import com.paymentx.controlcenter.service.PaymentFlowService;
import com.paymentx.controlcenter.service.PostgresDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The dashboard's real, READ-ONLY Postgres API surface across
 * all 7 PaymentX databases. What it does: exposes a paginated listing
 * endpoint per business domain (participants, payments, routing rules,
 * audit events, notifications, reconciliation batches, settlement
 * files, report executions), plus per-database connection/Liquibase
 * status and table info. There is no PUT/POST/DELETE anywhere in this
 * controller and no endpoint accepts a raw SQL string or WHERE
 * fragment - every query PostgresDataService runs is hardcoded in a
 * repository class. {database} path variables are resolved through
 * PostgresDatabaseIdentifier.fromSlug(), so an unknown slug is a 400,
 * never an arbitrary connection attempt. Why it exists: this is the
 * actual Phase 2 Postgres requirement, satisfied without ever letting
 * React touch Postgres directly.
 *
 * HINGLISH: Dashboard ka real, READ-ONLY Postgres API surface, saare 7
 * PaymentX databases ke across. Ye kya karti hai: har business domain
 * ke liye ek paginated listing endpoint expose karta hai
 * (participants, payments, routing rules, audit events, notifications,
 * reconciliation batches, settlement files, report executions), plus
 * har-database connection/Liquibase status aur table info. Is
 * controller me kahin bhi PUT/POST/DELETE nahi hai aur koi endpoint
 * raw SQL string ya WHERE fragment accept nahi karta - PostgresDataService
 * jo bhi query chalata hai wo ek repository class me hardcoded hai.
 * {database} path variables PostgresDatabaseIdentifier.fromSlug() ke
 * through resolve hote hain, isliye ek unknown slug ek 400 hai, kabhi
 * ek arbitrary connection attempt nahi. Ye dashboard me kyu hai: yehi
 * actual Phase 2 Postgres requirement hai, React ko kabhi Postgres
 * directly touch karne diye bina satisfy kiya gaya.
 */
@RestController
@RequestMapping("/api/v1/postgres")
public class PostgresController {

    private final PostgresDataService service;
    private final PaymentFlowService paymentFlowService;

    public PostgresController(PostgresDataService service, PaymentFlowService paymentFlowService) {
        this.service = service;
        this.paymentFlowService = paymentFlowService;
    }

    @GetMapping("/databases")
    public ApiResponse<List<DatabaseStatus>> databases() {
        return ApiResponse.success(service.allDatabaseStatuses());
    }

    @GetMapping("/databases/{database}/status")
    public ApiResponse<DatabaseStatus> databaseStatus(@PathVariable String database) {
        return ApiResponse.success(service.databaseStatus(PostgresDatabaseIdentifier.fromSlug(database)));
    }

    @GetMapping("/databases/{database}/tables")
    public ApiResponse<List<TableInfo>> tables(@PathVariable String database) {
        return ApiResponse.success(service.tableInfo(PostgresDatabaseIdentifier.fromSlug(database)));
    }

    @GetMapping("/participants")
    public ApiResponse<PageResponse<ParticipantSummary>> participants(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(service.participants(page, size, search));
    }

    /**
     * ENGLISH: The Transaction Monitor's real endpoint - every filter
     * param is optional; when all are absent this behaves exactly like
     * a plain paginated list. sortField is resolved through
     * PaymentSortField.fromParam() (unknown values quietly fall back to
     * createdAt, never reach raw SQL).
     *
     * HINGLISH: Transaction Monitor ka real endpoint - har filter param
     * optional hai; jab sab absent hon toh ye bilkul ek plain paginated
     * list jaisa behave karta hai. sortField
     * PaymentSortField.fromParam() ke through resolve hota hai (unknown
     * values quietly createdAt par fallback ho jaate hain, kabhi raw
     * SQL tak nahi pahunchte).
     */
    @GetMapping("/payments")
    public ApiResponse<PageResponse<PaymentSummary>> payments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scheme,
            @RequestParam(required = false) String participantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false, defaultValue = "false") boolean sortAscending) {
        PaymentFilter filter = new PaymentFilter(search, status, scheme, participantId, dateFrom, dateTo,
                PaymentSortField.fromParam(sortField), sortAscending);
        return ApiResponse.success(service.paymentsFiltered(filter, page, size));
    }

    @GetMapping("/payments/stats")
    public ApiResponse<PaymentStatsSummary> paymentStats() {
        return ApiResponse.success(service.paymentStats());
    }

    @GetMapping("/payments/timeseries")
    public ApiResponse<List<PaymentTimeseriesBucket>> paymentTimeseries(@RequestParam(required = false) Integer windowMinutes) {
        return ApiResponse.success(service.paymentTimeseries(windowMinutes));
    }

    @GetMapping("/payments/by-reference/{reference}")
    public ApiResponse<PaymentSummary> paymentByReference(@PathVariable String reference) {
        return service.paymentByReference(reference)
                .map(ApiResponse::success)
                .orElseThrow(() -> new com.paymentx.controlcenter.exception.ControlCenterException(
                        "PAYMENT_NOT_FOUND", "No payment found with reference: " + reference));
    }

    @GetMapping("/payments/by-reference/{reference}/flow")
    public ApiResponse<PaymentFlowDetail> paymentFlow(@PathVariable String reference) {
        return ApiResponse.success(paymentFlowService.flowForReference(reference));
    }

    @GetMapping("/routing-rules")
    public ApiResponse<PageResponse<RoutingRuleSummary>> routingRules(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(service.routingRules(page, size, search));
    }

    @GetMapping("/audit-events")
    public ApiResponse<PageResponse<AuditEventSummary>> auditEvents(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(service.auditEvents(page, size, search));
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationSummary>> notifications(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(service.notifications(page, size, search));
    }

    @GetMapping("/reconciliation-batches")
    public ApiResponse<PageResponse<ReconciliationBatchSummary>> reconciliationBatches(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.reconciliationBatches(page, size));
    }

    @GetMapping("/reconciliation-records")
    public ApiResponse<PageResponse<ReconciliationRecordSummary>> reconciliationRecords(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status) {
        return ApiResponse.success(service.reconciliationRecords(page, size, search, status));
    }

    @GetMapping("/settlement-files")
    public ApiResponse<PageResponse<SettlementFileSummary>> settlementFiles(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.settlementFiles(page, size));
    }

    @GetMapping("/report-executions")
    public ApiResponse<PageResponse<ReportExecutionSummary>> reportExecutions(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.reportExecutions(page, size));
    }

    @GetMapping("/report-files")
    public ApiResponse<PageResponse<ReportFileSummary>> reportFiles(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.reportFiles(page, size));
    }
}
