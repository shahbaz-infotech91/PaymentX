package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.PageResponse;
import com.paymentx.controlcenter.dto.postgres.*;
import com.paymentx.controlcenter.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ENGLISH: The service layer between the 8 Postgres repositories and
 * the controllers, responsible for the one shared safety rule every
 * listing endpoint needs: clamping page/size to sane bounds (page >=
 * 0, 1 <= size <= 200) before any repository ever runs a query - so a
 * malformed or hostile page/size request can never turn into an
 * unbounded LIMIT/OFFSET query against a real database. What it does:
 * wraps each repository's page + count calls into one PageResponse.
 * Why it exists: keeps this one clamping rule in exactly one place
 * instead of duplicated across 8 controllers.
 *
 * HINGLISH: 8 Postgres repositories aur controllers ke beech ka
 * service layer, jo us ek shared safety rule ke liye zimmedar hai jo
 * har listing endpoint ko chahiye: kisi bhi repository ke query
 * chalane se pehle page/size ko sensible bounds tak clamp karna (page
 * >= 0, 1 <= size <= 200) - taaki ek malformed ya hostile page/size
 * request kabhi bhi ek real database ke against unbounded LIMIT/OFFSET
 * query na ban jaye. Ye kya karti hai: har repository ki page + count
 * calls ko ek PageResponse me wrap karta hai. Ye dashboard me kyu hai:
 * is ek clamping rule ko exactly ek jagah rakhta hai, 8 controllers me
 * duplicate karne ke bajaye.
 */
@Service
public class PostgresDataService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private final ParticipantRepository participantRepository;
    private final PaymentRepository paymentRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationRepository notificationRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final DatabaseStatusRepository databaseStatusRepository;
    private final ControlCenterProperties properties;

    public PostgresDataService(ParticipantRepository participantRepository,
                                PaymentRepository paymentRepository,
                                RoutingRuleRepository routingRuleRepository,
                                AuditEventRepository auditEventRepository,
                                NotificationRepository notificationRepository,
                                ReconciliationRepository reconciliationRepository,
                                ReportExecutionRepository reportExecutionRepository,
                                DatabaseStatusRepository databaseStatusRepository,
                                ControlCenterProperties properties) {
        this.participantRepository = participantRepository;
        this.paymentRepository = paymentRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.auditEventRepository = auditEventRepository;
        this.notificationRepository = notificationRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.databaseStatusRepository = databaseStatusRepository;
        this.properties = properties;
    }

    private int clampPage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int clampSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    public PageResponse<ParticipantSummary> participants(Integer page, Integer size, String search) {
        int p = clampPage(page), s = clampSize(size);
        if (search != null && !search.isBlank()) {
            String q = search.trim();
            return new PageResponse<>(participantRepository.search(q, p, s), p, s, participantRepository.countSearch(q));
        }
        return new PageResponse<>(participantRepository.findPage(p, s), p, s, participantRepository.countAll());
    }

    public PageResponse<PaymentSummary> payments(Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(paymentRepository.findPage(p, s), p, s, paymentRepository.countAll());
    }

    public PageResponse<PaymentSummary> paymentsFiltered(PaymentFilter filter, Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(paymentRepository.findFiltered(filter, p, s), p, s, paymentRepository.countFiltered(filter));
    }

    public PaymentStatsSummary paymentStats() {
        return paymentRepository.stats();
    }

    public java.util.Optional<PaymentSummary> paymentByReference(String reference) {
        return paymentRepository.findByReference(reference);
    }

    public List<PaymentTimeseriesBucket> paymentTimeseries(Integer windowMinutes) {
        return paymentRepository.timeseries(windowMinutes);
    }

    public PageResponse<RoutingRuleSummary> routingRules(Integer page, Integer size, String search) {
        int p = clampPage(page), s = clampSize(size);
        if (search != null && !search.isBlank()) {
            String q = search.trim();
            return new PageResponse<>(routingRuleRepository.search(q, p, s), p, s, routingRuleRepository.countSearch(q));
        }
        return new PageResponse<>(routingRuleRepository.findPage(p, s), p, s, routingRuleRepository.countAll());
    }

    public PageResponse<AuditEventSummary> auditEvents(Integer page, Integer size, String search) {
        int p = clampPage(page), s = clampSize(size);
        if (search != null && !search.isBlank()) {
            String q = search.trim();
            return new PageResponse<>(auditEventRepository.search(q, p, s), p, s, auditEventRepository.countSearch(q));
        }
        return new PageResponse<>(auditEventRepository.findPage(p, s), p, s, auditEventRepository.countAll());
    }

    public PageResponse<NotificationSummary> notifications(Integer page, Integer size, String search) {
        int p = clampPage(page), s = clampSize(size);
        if (search != null && !search.isBlank()) {
            String q = search.trim();
            return new PageResponse<>(notificationRepository.search(q, p, s), p, s, notificationRepository.countSearch(q));
        }
        return new PageResponse<>(notificationRepository.findPage(p, s), p, s, notificationRepository.countAll());
    }

    public PageResponse<ReconciliationBatchSummary> reconciliationBatches(Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(reconciliationRepository.findBatchPage(p, s), p, s, reconciliationRepository.countBatches());
    }

    public PageResponse<ReconciliationRecordSummary> reconciliationRecords(Integer page, Integer size, String search, String status) {
        int p = clampPage(page), s = clampSize(size);
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasSearch || hasStatus) {
            String q = hasSearch ? search.trim() : "";
            return new PageResponse<>(reconciliationRepository.searchRecords(q, status, p, s), p, s, reconciliationRepository.countSearchRecords(q, status));
        }
        return new PageResponse<>(reconciliationRepository.findRecordPage(p, s), p, s, reconciliationRepository.countRecords());
    }

    public PageResponse<SettlementFileSummary> settlementFiles(Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(reconciliationRepository.findSettlementFilePage(p, s), p, s, reconciliationRepository.countSettlementFiles());
    }

    public PageResponse<ReportExecutionSummary> reportExecutions(Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(reportExecutionRepository.findPage(p, s), p, s, reportExecutionRepository.countAll());
    }

    public PageResponse<ReportFileSummary> reportFiles(Integer page, Integer size) {
        int p = clampPage(page), s = clampSize(size);
        return new PageResponse<>(reportExecutionRepository.findReportFilePage(p, s), p, s, reportExecutionRepository.countReportFiles());
    }

    public List<DatabaseStatus> allDatabaseStatuses() {
        return List.of(PostgresDatabaseIdentifier.values()).stream()
                .map(id -> databaseStatusRepository.checkStatus(id, id.resolveDatabaseName(properties.getPostgres())))
                .toList();
    }

    public DatabaseStatus databaseStatus(PostgresDatabaseIdentifier identifier) {
        return databaseStatusRepository.checkStatus(identifier, identifier.resolveDatabaseName(properties.getPostgres()));
    }

    public List<TableInfo> tableInfo(PostgresDatabaseIdentifier identifier) {
        return databaseStatusRepository.tableInfo(identifier);
    }
}
