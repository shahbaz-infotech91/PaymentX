package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.e2e.E2ERunResult;
import com.paymentx.controlcenter.exception.ControlCenterException;
import com.paymentx.controlcenter.service.E2EFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real Phase 5 E2E Payment Flow API surface -
 * POST /api/v1/e2e/run starts one real, bounded E2E run in the
 * background and returns its initial real snapshot immediately (runId
 * + RUNNING); GET /api/v1/e2e/run/{runId} returns that run's current
 * real snapshot (the frontend polls this for "live stage state"); GET
 * /api/v1/e2e/history returns the bounded in-memory list of recent
 * real runs. Every call delegates straight to E2EFlowService, which
 * never fabricates a result.
 *
 * HINGLISH: Dashboard ka real Phase 5 E2E Payment Flow API surface -
 * POST /api/v1/e2e/run background me ek real, bounded E2E run start
 * karta hai aur turant uska initial real snapshot return karta hai
 * (runId + RUNNING); GET /api/v1/e2e/run/{runId} us run ka current
 * real snapshot return karta hai (frontend "live stage state" ke liye
 * ise poll karta hai); GET /api/v1/e2e/history recent real runs ki
 * bounded in-memory list return karta hai. Har call seedha
 * E2EFlowService ko delegate karta hai, jo kabhi ek result fabricate
 * nahi karta.
 */
@RestController
@RequestMapping("/api/v1/e2e")
public class E2EController {

    private final E2EFlowService e2eFlowService;

    public E2EController(E2EFlowService e2eFlowService) {
        this.e2eFlowService = e2eFlowService;
    }

    @PostMapping("/run")
    public ApiResponse<E2ERunResult> startRun() {
        return ApiResponse.success(e2eFlowService.startRun());
    }

    @GetMapping("/run/{runId}")
    public ApiResponse<E2ERunResult> getRun(@PathVariable String runId) {
        return e2eFlowService.getRun(runId)
                .map(ApiResponse::success)
                .orElseThrow(() -> new ControlCenterException("E2E_RUN_NOT_FOUND", "No E2E run found with id: " + runId));
    }

    @GetMapping("/history")
    public ApiResponse<List<E2ERunResult>> history() {
        return ApiResponse.success(e2eFlowService.history());
    }
}
