package com.paymentx.vector.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.vector.dto.StoreDocumentRequest;
import com.paymentx.vector.dto.StoreDocumentResponse;
import com.paymentx.vector.dto.VectorHealthResponse;
import com.paymentx.vector.dto.VectorSearchRequest;
import com.paymentx.vector.dto.VectorSearchResponse;
import com.paymentx.vector.service.VectorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * English:
 * The entire Phase 3.5 Vector Service API surface - store, search,
 * delete, health, following the exact `/api/v1/{resource}` +
 * ApiResponse&lt;T&gt; envelope convention every other PaymentX service
 * uses. WHY storeDocument/deleteDocument require VECTOR_ADMIN while
 * search/health stay open: matches PromptController's exact "open
 * reads, admin-gated writes" split (see that controller's javadoc) -
 * search is the routine, non-admin operation a future RAG Service needs
 * to call as part of answering every query; store/delete are real
 * mutating, ingestion-adjacent operations. This controller does NOT
 * generate embeddings and does NOT accept raw text anywhere (Step 14 -
 * "This service receives an embedding vector. It does NOT generate
 * embeddings.") - every embedding in every request body here is already
 * a real vector a caller obtained from Embedding Service.
 * Why it exists: Step 14/15/29/31's exact endpoint list.
 * How it communicates with other components: this IS the backend
 * endpoint a future RAG Service will call for both ingestion
 * (storeDocument, once a document-ingestion pipeline exists, Phase
 * 3.6+) and retrieval (search) - today, called by nothing in
 * production, same "contract exists before its caller does" pattern
 * every other AI Platform service's controller documents.
 *
 * Hinglish:
 * Poora Phase 3.5 Vector Service API surface - store, search, delete,
 * health, har doosri PaymentX service ke exact `/api/v1/{resource}` +
 * ApiResponse&lt;T&gt; envelope convention ko follow karte hue.
 * storeDocument/deleteDocument ko VECTOR_ADMIN KYU chahiye jabki
 * search/health open rehte hain: PromptController ke exact "open reads,
 * admin-gated writes" split se match karta hai (us controller ka
 * javadoc dekho) - search wo routine, non-admin operation hai jise ek
 * future RAG Service ko har query ka jawab dene ke hisse ke roop me
 * call karna hota hai; store/delete real mutating, ingestion-adjacent
 * operations hain. Ye controller kabhi embeddings generate NAHI karta
 * aur kahin bhi raw text accept NAHI karta (Step 14 - "Ye service ek
 * embedding vector receive karti hai. Ye embeddings generate NAHI
 * karti.") - yahan har request body me har embedding already ek real
 * vector hai jo ek caller ne Embedding Service se liya.
 * Ye kyu hai: Step 14/15/29/31 ki exact endpoint list.
 * Dusre components se kaise communicate karta hai: yehi backend
 * endpoint hai jise ek future RAG Service dono ingestion (storeDocument,
 * ek baar document-ingestion pipeline exist kare, Phase 3.6+) aur
 * retrieval (search) ke liye call karegi - aaj, production me kuch bhi
 * ise call nahi karta, wahi "contract apne caller se pehle exist karta
 * hai" pattern jo har doosri AI Platform service ka controller document
 * karta hai.
 */
@RestController
@RequestMapping("/api/v1/vector")
@RequiredArgsConstructor
@Tag(name = "Vector", description = "Real document/chunk/embedding storage and pgvector top-K similarity search")
public class VectorController {

    private final VectorStoreService vectorStoreService;

    @PostMapping("/documents")
    @PreAuthorize("hasRole('VECTOR_ADMIN')")
    @Operation(summary = "Store (or upsert) a document's chunks and their already-computed embeddings (admin only)")
    public ResponseEntity<ApiResponse<StoreDocumentResponse>> storeDocument(@Valid @RequestBody StoreDocumentRequest request) {
        StoreDocumentResponse response = vectorStoreService.storeDocument(request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ApiResponse.success(response));
    }

    @DeleteMapping("/documents/{documentKey}/versions/{documentVersion}")
    @PreAuthorize("hasRole('VECTOR_ADMIN')")
    @Operation(summary = "Delete a document and, via database cascade, all of its chunks and embeddings (admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable String documentKey, @PathVariable String documentVersion) {
        vectorStoreService.deleteDocument(documentKey, documentVersion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    @Operation(summary = "Top-K cosine-similarity search over stored embeddings, with optional metadata filtering - open to any caller reaching this service")
    public ResponseEntity<ApiResponse<VectorSearchResponse>> search(@Valid @RequestBody VectorSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vectorStoreService.search(request)));
    }

    @GetMapping("/health")
    @Operation(summary = "Report real PostgreSQL connectivity and pgvector extension availability - does not run a vector search")
    public ResponseEntity<ApiResponse<VectorHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success(vectorStoreService.health()));
    }
}
