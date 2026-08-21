package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.dto.search.SearchResult;
import com.paymentx.controlcenter.repository.GlobalSearchRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ENGLISH: Fans a real search term out across the 3 real, searchable
 * domains (payments, participants, settlement files) and combines the
 * real results into one list - a blank/too-short query returns an
 * empty list rather than an expensive full-table scan.
 *
 * HINGLISH: Ek real search term ko 3 real, searchable domains
 * (payments, participants, settlement files) ke across fan-out karta
 * hai aur real results ko ek list me combine karta hai - ek blank/
 * bahut-chhota query ek expensive full-table scan ke bajaye ek empty
 * list return karta hai.
 */
@Service
public class GlobalSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int RESULTS_PER_DOMAIN = 8;

    private final GlobalSearchRepository repository;

    public GlobalSearchService(GlobalSearchRepository repository) {
        this.repository = repository;
    }

    public List<SearchResult> search(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String trimmed = query.trim();
        List<SearchResult> results = new ArrayList<>();
        results.addAll(repository.searchPayments(trimmed, RESULTS_PER_DOMAIN));
        results.addAll(repository.searchParticipants(trimmed, RESULTS_PER_DOMAIN));
        results.addAll(repository.searchSettlementFiles(trimmed, RESULTS_PER_DOMAIN));
        return results;
    }
}
