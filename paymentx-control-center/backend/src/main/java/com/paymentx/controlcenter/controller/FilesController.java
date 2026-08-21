package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.files.FileEntry;
import com.paymentx.controlcenter.service.SafeFileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real, safe file browser (Phase 4) - scoped
 * to exactly ONE explicitly-configured directory (the real PaymentX
 * reporting exports folder), never arbitrary filesystem browsing. What
 * it does: GET /api/v1/files lists real files in that one directory;
 * GET /api/v1/files/download/{filename} streams one real file back,
 * after SafeFileService validates the filename can never escape that
 * directory. No endpoint here accepts a directory path from the
 * browser - the root is always the server-configured one.
 *
 * HINGLISH: Dashboard ka real, safe file browser (Phase 4) - exactly
 * EK explicitly-configured directory tak scoped (real PaymentX
 * reporting exports folder), kabhi arbitrary filesystem browsing
 * nahi. Ye kya karti hai: GET /api/v1/files us ek directory ki real
 * files list karta hai; GET /api/v1/files/download/{filename} ek real
 * file wapas stream karta hai, SafeFileService ke filename validate
 * karne ke baad ki wo us directory se kabhi escape nahi kar sakta.
 * Yahan koi endpoint browser se directory path accept nahi karta -
 * root hamesha server-configured hota hai.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FilesController {

    private final SafeFileService safeFileService;

    public FilesController(SafeFileService safeFileService) {
        this.safeFileService = safeFileService;
    }

    @GetMapping
    public ApiResponse<List<FileEntry>> list() {
        return ApiResponse.success(safeFileService.listFiles());
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Resource resource = safeFileService.resolveForDownload(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
