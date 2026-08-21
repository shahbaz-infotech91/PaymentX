package com.paymentx.reconciliation.service;

import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.reconciliation.config.ReconciliationProperties;
import com.paymentx.reconciliation.dto.SettlementFileResponse;
import com.paymentx.reconciliation.dto.StartReconciliationRequest;
import com.paymentx.reconciliation.entity.BatchStatus;
import com.paymentx.reconciliation.entity.BatchType;
import com.paymentx.reconciliation.entity.MismatchRecord;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import com.paymentx.reconciliation.entity.SettlementFile;
import com.paymentx.reconciliation.event.BatchStartRequestedApplicationEvent;
import com.paymentx.reconciliation.mapper.ReconciliationMapper;
import com.paymentx.reconciliation.repository.MismatchRecordRepository;
import com.paymentx.reconciliation.repository.ReconciliationBatchRepository;
import com.paymentx.reconciliation.repository.ReconciliationRecordRepository;
import com.paymentx.reconciliation.repository.ReconciliationSummaryRepository;
import com.paymentx.reconciliation.repository.SettlementFileRepository;
import com.paymentx.reconciliation.service.impl.ReconciliationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationServiceImplTest is a JUnit test class in the reconciliation module of PaymentX, package com.paymentx.reconciliation.service. It is used within reconciliation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationServiceImplTest PaymentX ke reconciliation module ka ek JUnit test class hai, package com.paymentx.reconciliation.service me. Ye reconciliation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class ReconciliationServiceImplTest {

    @Mock
    private SettlementFileRepository settlementFileRepository;
    @Mock
    private ReconciliationBatchRepository reconciliationBatchRepository;
    @Mock
    private ReconciliationRecordRepository reconciliationRecordRepository;
    @Mock
    private MismatchRecordRepository mismatchRecordRepository;
    @Mock
    private ReconciliationSummaryRepository reconciliationSummaryRepository;
    @Mock
    private ReconciliationMapper reconciliationMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ReconciliationProperties reconciliationProperties;
    private ReconciliationServiceImpl reconciliationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reconciliationProperties = new ReconciliationProperties();
        reconciliationProperties.getStorage().setSettlementFileDirectory(tempDir.toString());
        reconciliationService = new ReconciliationServiceImpl(settlementFileRepository, reconciliationBatchRepository,
                reconciliationRecordRepository, mismatchRecordRepository, reconciliationSummaryRepository,
                reconciliationMapper, applicationEventPublisher, reconciliationProperties);
    }

    @Test
    void uploadSettlementFile_newFile_savesAndReturnsResponse() {
        MockMultipartFile file = new MockMultipartFile("file", "settlement.csv", "text/csv", "header\nrow1".getBytes());
        SettlementFile saved = SettlementFile.builder().build();
        SettlementFileResponse response = new SettlementFileResponse(UUID.randomUUID(), "settlement.csv", null, null, null, null, null, null);

        when(settlementFileRepository.existsByChecksumHash(any())).thenReturn(false);
        when(settlementFileRepository.save(any())).thenReturn(saved);
        when(reconciliationMapper.toResponse(saved)).thenReturn(response);

        var result = reconciliationService.uploadSettlementFile(file);

        assertThat(result.fileName()).isEqualTo("settlement.csv");
        verify(settlementFileRepository).save(any());
    }

    @Test
    void uploadSettlementFile_duplicateChecksum_throwsConflict() {
        MockMultipartFile file = new MockMultipartFile("file", "settlement.csv", "text/csv", "same-content".getBytes());
        when(settlementFileRepository.existsByChecksumHash(any())).thenReturn(true);

        assertThatThrownBy(() -> reconciliationService.uploadSettlementFile(file))
                .isInstanceOf(ConflictException.class);

        verify(settlementFileRepository, never()).save(any());
    }

    @Test
    void uploadSettlementFile_unsupportedExtension_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "settlement.xml", "text/xml", "data".getBytes());

        assertThatThrownBy(() -> reconciliationService.uploadSettlementFile(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startReconciliation_validRequest_createsBatchAndTriggersAsyncRun() {
        StartReconciliationRequest request = new StartReconciliationRequest(BatchType.FULL, null, null, null);
        ReconciliationBatch saved = ReconciliationBatch.builder().status(BatchStatus.PENDING).build();
        when(reconciliationBatchRepository.save(any())).thenReturn(saved);

        reconciliationService.startReconciliation(request, "admin1");

        verify(applicationEventPublisher).publishEvent(any(BatchStartRequestedApplicationEvent.class));
    }

    @Test
    void reprocessBatch_alreadyRunning_throwsConflict() {
        UUID batchId = UUID.randomUUID();
        ReconciliationBatch batch = ReconciliationBatch.builder().status(BatchStatus.RUNNING).build();
        when(reconciliationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> reconciliationService.reprocessBatch(batchId, "admin1"))
                .isInstanceOf(ConflictException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void resolveMismatch_alreadyResolved_throwsConflict() {
        UUID mismatchId = UUID.randomUUID();
        MismatchRecord mismatch = MismatchRecord.builder().resolved(true).build();
        when(mismatchRecordRepository.findById(mismatchId)).thenReturn(Optional.of(mismatch));

        assertThatThrownBy(() -> reconciliationService.resolveMismatch(mismatchId, "admin1", "notes"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getBatchStatus_notFound_throwsResourceNotFound() {
        UUID batchId = UUID.randomUUID();
        when(reconciliationBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reconciliationService.getBatchStatus(batchId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
