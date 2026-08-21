package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.PageResponse;
import com.paymentx.controlcenter.dto.postgres.ParticipantSummary;
import com.paymentx.controlcenter.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ENGLISH: Proves the one safety rule PostgresDataService exists for -
 * page/size clamping - without touching a real database (repositories
 * are mocked). What it does: a negative page becomes 0, a null size
 * becomes the real default (20), and an oversized requested size is
 * capped at the real max (200) - so a hostile or malformed page/size
 * value can never reach a repository's LIMIT/OFFSET query unclamped.
 *
 * HINGLISH: PostgresDataService jis ek safety rule ke liye exist karta
 * hai use prove karta hai - page/size clamping - kisi real database ko
 * touch kiye bina (repositories mocked hain). Ye kya karti hai: ek
 * negative page 0 ban jaata hai, ek null size real default (20) ban
 * jaata hai, aur ek oversized requested size real max (200) par cap
 * ho jaata hai - taaki ek hostile ya malformed page/size value kabhi
 * bhi ek repository ki LIMIT/OFFSET query tak unclamped na pahunche.
 */
@ExtendWith(MockitoExtension.class)
class PostgresDataServiceTest {

    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RoutingRuleRepository routingRuleRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ReconciliationRepository reconciliationRepository;
    @Mock
    private ReportExecutionRepository reportExecutionRepository;
    @Mock
    private DatabaseStatusRepository databaseStatusRepository;

    private PostgresDataService service() {
        return new PostgresDataService(participantRepository, paymentRepository, routingRuleRepository,
                auditEventRepository, notificationRepository, reconciliationRepository, reportExecutionRepository,
                databaseStatusRepository, new ControlCenterProperties());
    }

    @Test
    void negativePageIsClampedToZero() {
        when(participantRepository.findPage(eq(0), anyInt())).thenReturn(List.of());
        when(participantRepository.countAll()).thenReturn(0L);

        PageResponse<ParticipantSummary> result = service().participants(-5, 10, null);

        assertThat(result.page()).isZero();
        verify(participantRepository).findPage(0, 10);
    }

    @Test
    void nullSizeFallsBackToTheRealDefault() {
        when(participantRepository.findPage(anyInt(), eq(20))).thenReturn(List.of());
        when(participantRepository.countAll()).thenReturn(0L);

        PageResponse<ParticipantSummary> result = service().participants(0, null, null);

        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    void oversizedRequestIsCappedAtTheRealMax() {
        when(participantRepository.findPage(anyInt(), eq(200))).thenReturn(List.of());
        when(participantRepository.countAll()).thenReturn(0L);

        PageResponse<ParticipantSummary> result = service().participants(0, 100_000, null);

        assertThat(result.size()).isEqualTo(200);
    }
}
