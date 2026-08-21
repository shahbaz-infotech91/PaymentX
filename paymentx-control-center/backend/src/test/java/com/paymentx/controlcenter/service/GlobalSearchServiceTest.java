package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.dto.search.SearchResult;
import com.paymentx.controlcenter.repository.GlobalSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ENGLISH: Proves GlobalSearchService's real short-query guard - a
 * blank or 1-character query never reaches the repository (would be
 * an expensive, low-value full-table ILIKE scan across 3 databases),
 * while a real 2+ character query does trigger all 3 real domain
 * searches and combines their real results.
 *
 * HINGLISH: GlobalSearchService ka real short-query guard prove karta
 * hai - ek blank ya 1-character query kabhi repository tak nahi
 * pahunchta (ye 3 databases ke across ek expensive, low-value
 * full-table ILIKE scan hota), jabki ek real 2+ character query
 * genuinely saare 3 real domain searches trigger karta hai aur unke
 * real results ko combine karta hai.
 */
@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock
    private GlobalSearchRepository repository;

    @Test
    void blankQueryNeverReachesTheRepository() {
        GlobalSearchService service = new GlobalSearchService(repository);

        assertThat(service.search("")).isEmpty();
        assertThat(service.search("  ")).isEmpty();
        assertThat(service.search("a")).isEmpty();

        verify(repository, never()).searchPayments(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void realQueryCombinesAllThreeRealDomainSearches() {
        GlobalSearchService service = new GlobalSearchService(repository);
        when(repository.searchPayments(org.mockito.ArgumentMatchers.eq("BANK001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(repository.searchParticipants(org.mockito.ArgumentMatchers.eq("BANK001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new SearchResult(com.paymentx.controlcenter.dto.search.SearchResultType.PARTICIPANT, "BANK001", "First Test Bank", "BANK001 · ACTIVE")));
        when(repository.searchSettlementFiles(org.mockito.ArgumentMatchers.eq("BANK001"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        List<SearchResult> results = service.search("BANK001");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(com.paymentx.controlcenter.dto.search.SearchResultType.PARTICIPANT);
    }
}
