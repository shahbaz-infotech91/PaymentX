package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.apitester.ApiTesterEndpointDescriptor;
import com.paymentx.controlcenter.dto.apitester.ApiTesterRequest;
import com.paymentx.controlcenter.dto.apitester.ApiTesterResponse;
import com.paymentx.controlcenter.service.ApiTesterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real Phase 5 API Tester surface - GET
 * /api/v1/api-tester/endpoints returns the real, fixed allowlist
 * (drives the frontend's method/URL pickers so a user can only ever
 * select a real, allowed combination); POST /api/v1/api-tester/execute
 * proxies exactly one real, allowlisted call and returns its real
 * response. There is no endpoint here that accepts a raw URL - see
 * ApiTesterService/ApiTesterAllowlist for the actual enforcement.
 *
 * HINGLISH: Dashboard ka real Phase 5 API Tester surface - GET
 * /api/v1/api-tester/endpoints real, fixed allowlist return karta hai
 * (frontend ke method/URL pickers ko drive karta hai taaki user kabhi
 * sirf ek real, allowed combination hi select kar sake); POST
 * /api/v1/api-tester/execute exactly ek real, allowlisted call proxy
 * karta hai aur uska real response return karta hai. Yahan koi
 * endpoint nahi hai jo raw URL accept kare - actual enforcement ke
 * liye ApiTesterService/ApiTesterAllowlist dekho.
 */
@RestController
@RequestMapping("/api/v1/api-tester")
public class ApiTesterController {

    private final ApiTesterService apiTesterService;

    public ApiTesterController(ApiTesterService apiTesterService) {
        this.apiTesterService = apiTesterService;
    }

    @GetMapping("/endpoints")
    public ApiResponse<List<ApiTesterEndpointDescriptor>> endpoints() {
        return ApiResponse.success(apiTesterService.allowedEndpoints());
    }

    @PostMapping("/execute")
    public ApiResponse<ApiTesterResponse> execute(@RequestBody ApiTesterRequest request) {
        return ApiResponse.success(apiTesterService.execute(request));
    }
}
