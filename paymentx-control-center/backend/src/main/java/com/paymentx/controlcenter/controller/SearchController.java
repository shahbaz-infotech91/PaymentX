package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.search.SearchResult;
import com.paymentx.controlcenter.service.GlobalSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real Global Search API - GET
 * /api/v1/search?q=... runs the real term against payments,
 * participants, and settlement files, returning real matches only. A
 * query shorter than 2 characters returns an empty list rather than
 * scanning every row.
 *
 * HINGLISH: Dashboard ka real Global Search API - GET
 * /api/v1/search?q=... real term ko payments, participants, aur
 * settlement files ke against chalata hai, sirf real matches return
 * karte hue. 2 characters se chhota query har row scan karne ke
 * bajaye ek empty list return karta hai.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final GlobalSearchService service;

    public SearchController(GlobalSearchService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SearchResult>> search(@RequestParam String q) {
        return ApiResponse.success(service.search(q));
    }
}
