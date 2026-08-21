package com.paymentx.vector.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.vector.config.VectorProperties;
import com.paymentx.vector.dto.StoreDocumentRequest;
import com.paymentx.vector.dto.StoreDocumentResponse;
import com.paymentx.vector.dto.VectorHealthResponse;
import com.paymentx.vector.dto.VectorSearchRequest;
import com.paymentx.vector.dto.VectorSearchResponse;
import com.paymentx.vector.dto.VectorSearchResultItem;
import com.paymentx.vector.entity.AiDocument;
import com.paymentx.vector.entity.AiDocumentChunk;
import com.paymentx.vector.entity.AiDocumentEmbedding;
import com.paymentx.vector.entity.DocumentStatus;
import com.paymentx.vector.exception.VectorException;
import com.paymentx.vector.metrics.VectorMetrics;
import com.paymentx.vector.repository.AiDocumentChunkRepository;
import com.paymentx.vector.repository.AiDocumentEmbeddingRepository;
import com.paymentx.vector.repository.AiDocumentRepository;
import com.paymentx.vector.service.VectorStoreService;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * English:
 * The real implementation of VectorStoreService - owns every real
 * invariant this phase requires: input validation before any write
 * (Step 22), find-or-create upsert semantics keyed on real database
 * unique constraints (Step 16/17), one transaction boundary per store
 * call spanning document+chunks+embeddings (Step 24), and the
 * pgvector-literal/JSONB-literal formatting the native similarity-search
 * query needs (see AiDocumentEmbeddingRepository's javadoc for why that
 * query is native SQL).
 * WHY documentType/source/documentVersion are merged INTO the stored
 * `ai_document.metadata` JSONB rather than kept only in their own real
 * columns: Step 10/20 want ALL metadata filtering - including the
 * well-known fields Step 5 also gave real columns to - expressed
 * through ONE mechanism (JSONB containment, `mergeDocumentMetadata`),
 * not two (a mix of column-equality checks for "known" keys and JSONB
 * checks for "custom" ones inside the search query). The real columns
 * still exist and remain the source of truth for direct lookups
 * (findByDocumentKeyAndDocumentVersion); the metadata copy exists
 * specifically to make the search path's filtering uniform and simple.
 * WHY store/delete are @Transactional but search/health are
 * @Transactional(readOnly = true): a store spans 1-to-N INSERT/UPDATE
 * statements across three tables that must all succeed or all roll back
 * together (Step 24) - a real DataIntegrityViolationException from a
 * concurrent duplicate racing past this service's own
 * find-or-create check (Step 25) rolls back everything written so far
 * in that call, leaving no partial document/chunk/embedding state,
 * and is mapped to a real 409 by GlobalExceptionHandler.
 * Why it exists: PAYMENTX_PHASE_3_ARCHITECTURE.md-style VectorStoreService
 * layer - the piece VectorController depends on instead of talking to
 * the three repositories directly.
 * How it communicates with other components: implements
 * VectorStoreService; injected into VectorController; calls
 * AiDocumentRepository/AiDocumentChunkRepository/
 * AiDocumentEmbeddingRepository and VectorMetrics.
 *
 * Hinglish:
 * VectorStoreService ki real implementation - is phase ke har real
 * invariant ki malik hai: kisi bhi write se pehle input validation
 * (Step 22), real database unique constraints par keyed find-or-create
 * upsert semantics (Step 16/17), document+chunks+embeddings ko span
 * karta ek transaction boundary per store call (Step 24), aur
 * pgvector-literal/JSONB-literal formatting jo native similarity-search
 * query ko chahiye (AiDocumentEmbeddingRepository ka javadoc dekho ki
 * wo query native SQL kyun hai).
 * documentType/source/documentVersion stored `ai_document.metadata`
 * JSONB me MERGE KYU hote hain, sirf apne real columns me rakhne ke
 * bajaye: Step 10/20 chahte hain ki SAARI metadata filtering - well-
 * known fields including jinhe Step 5 ne bhi real columns diye - ek hi
 * mechanism (JSONB containment, `mergeDocumentMetadata`) ke through
 * express ho, do nahi (search query ke andar "known" keys ke liye
 * column-equality checks aur "custom" ke liye JSONB checks ka mix). Real
 * columns ab bhi exist karte hain aur direct lookups ke liye source of
 * truth rehte hain (findByDocumentKeyAndDocumentVersion); metadata copy
 * specifically search path ki filtering ko uniform aur simple banane ke
 * liye exist karta hai.
 * store/delete @Transactional lekin search/health
 * @Transactional(readOnly = true) KYU hain: ek store call teen tables
 * ke across 1-se-N INSERT/UPDATE statements span karta hai jo sab
 * succeed ya sab rollback saath hone chahiye (Step 24) - is service ke
 * apne find-or-create check ko race karta ek concurrent duplicate se ek
 * real DataIntegrityViolationException (Step 25) us call me ab tak jo
 * bhi likha gaya use rollback kar deta hai, koi partial
 * document/chunk/embedding state nahi chhodta, aur GlobalExceptionHandler
 * dwara ek real 409 par map hota hai.
 * Ye kyu hai: PAYMENTX_PHASE_3_ARCHITECTURE.md-style VectorStoreService
 * layer - wo piece jispar VectorController depend karta hai seedhe teen
 * repositories se baat karne ke bajaye.
 * Dusre components se kaise communicate karta hai: VectorStoreService
 * implement karta hai; VectorController me inject hota hai;
 * AiDocumentRepository/AiDocumentChunkRepository/
 * AiDocumentEmbeddingRepository aur VectorMetrics ko call karta hai.
 */
@Service
@Slf4j
public class VectorStoreServiceImpl implements VectorStoreService {

    private final AiDocumentRepository documentRepository;
    private final AiDocumentChunkRepository chunkRepository;
    private final AiDocumentEmbeddingRepository embeddingRepository;
    private final VectorProperties properties;
    private final VectorMetrics metrics;
    private final ObjectMapper objectMapper;

    public VectorStoreServiceImpl(AiDocumentRepository documentRepository,
                                   AiDocumentChunkRepository chunkRepository,
                                   AiDocumentEmbeddingRepository embeddingRepository,
                                   VectorProperties properties,
                                   VectorMetrics metrics,
                                   ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StoreDocumentResponse storeDocument(StoreDocumentRequest request) {
        metrics.recordStoreRequest();
        Timer.Sample timer = metrics.startInsertTimer();
        try {
            for (int i = 0; i < request.chunks().size(); i++) {
                validateVector(request.chunks().get(i).embedding(), "chunks[" + i + "].embedding");
            }

            boolean created;
            AiDocument document = documentRepository.findByDocumentKeyAndDocumentVersion(request.documentKey(), request.documentVersion()).orElse(null);
            if (document == null) {
                created = true;
                document = AiDocument.builder()
                        .documentKey(request.documentKey())
                        .documentName(request.documentName())
                        .documentType(request.documentType())
                        .source(request.source())
                        .documentVersion(request.documentVersion())
                        .status(DocumentStatus.ACTIVE)
                        .metadata(mergeDocumentMetadata(request))
                        .build();
            } else {
                created = false;
                document.setDocumentName(request.documentName());
                document.setDocumentType(request.documentType());
                document.setSource(request.source());
                document.setStatus(DocumentStatus.ACTIVE);
                document.setMetadata(mergeDocumentMetadata(request));
            }
            document = documentRepository.save(document);

            int embeddingCount = 0;
            for (int i = 0; i < request.chunks().size(); i++) {
                StoreDocumentRequest.ChunkInput chunkInput = request.chunks().get(i);

                AiDocumentChunk chunk = chunkRepository.findByDocumentIdAndChunkIndex(document.getId(), i).orElse(null);
                if (chunk == null) {
                    chunk = AiDocumentChunk.builder()
                            .documentId(document.getId())
                            .chunkIndex(i)
                            .content(chunkInput.content())
                            .metadata(chunkInput.metadata())
                            .build();
                } else {
                    chunk.setContent(chunkInput.content());
                    chunk.setMetadata(chunkInput.metadata());
                }
                chunk = chunkRepository.save(chunk);

                float[] vector = toFloatArray(chunkInput.embedding());
                AiDocumentEmbedding embedding = embeddingRepository
                        .findByChunkIdAndProviderAndModel(chunk.getId(), chunkInput.provider(), chunkInput.model())
                        .orElse(null);
                if (embedding == null) {
                    embedding = AiDocumentEmbedding.builder()
                            .chunkId(chunk.getId())
                            .embedding(vector)
                            .provider(chunkInput.provider())
                            .model(chunkInput.model())
                            .dimension(vector.length)
                            .build();
                } else {
                    embedding.setEmbedding(vector);
                    embedding.setDimension(vector.length);
                }
                embeddingRepository.save(embedding);
                embeddingCount++;
            }

            metrics.stopInsertTimer(timer);
            metrics.recordStoreSuccess(created);
            log.info("Document stored documentKey={} documentVersion={} created={} chunkCount={} embeddingCount={}",
                    request.documentKey(), request.documentVersion(), created, request.chunks().size(), embeddingCount);

            return new StoreDocumentResponse(document.getId(), document.getDocumentKey(), document.getDocumentVersion(),
                    created, request.chunks().size(), embeddingCount, document.getUpdatedAt());
        } catch (VectorException ex) {
            metrics.stopInsertTimer(timer);
            metrics.recordStoreFailure(ex.getErrorCode());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteDocument(String documentKey, String documentVersion) {
        AiDocument document = documentRepository.findByDocumentKeyAndDocumentVersion(documentKey, documentVersion)
                .orElseThrow(() -> VectorException.documentNotFound(
                        "No document found for documentKey=" + documentKey + " documentVersion=" + documentVersion));
        documentRepository.delete(document);
        metrics.recordDelete();
        log.info("Document deleted documentKey={} documentVersion={}", documentKey, documentVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public VectorSearchResponse search(VectorSearchRequest request) {
        validateVector(request.queryEmbedding(), "queryEmbedding");

        String provider = request.provider() != null && !request.provider().isBlank() ? request.provider() : properties.getDefaultProvider();
        String model = request.model() != null && !request.model().isBlank() ? request.model() : properties.getDefaultModel();

        int topK = request.topK() != null ? request.topK() : properties.getDefaultTopK();
        if (topK < 1 || topK > properties.getMaxTopK()) {
            throw VectorException.invalidTopK("topK must be between 1 and " + properties.getMaxTopK() + " (got " + topK + ").");
        }

        double maxDistance = request.minScore() != null ? Math.max(0.0, 1.0 - request.minScore()) : 2.0;
        String queryEmbeddingLiteral = toPgVectorLiteral(request.queryEmbedding());
        String filtersJson = toJson(request.filters() != null ? request.filters() : Map.of());

        Timer.Sample timer = metrics.startSearchTimer();
        List<Object[]> rows = embeddingRepository.topKSimilaritySearch(queryEmbeddingLiteral, provider, model, filtersJson, maxDistance, topK);
        metrics.stopSearchTimer(timer);

        List<VectorSearchResultItem> results = rows.stream().map(this::toResultItem).toList();
        metrics.recordSearch(results.size());
        log.info("Vector search completed provider={} model={} topK={} resultCount={}", provider, model, topK, results.size());

        return new VectorSearchResponse(results, results.size(), provider, model);
    }

    @Override
    public VectorHealthResponse health() {
        try {
            long documentCount = documentRepository.count();
            boolean pgvectorAvailable = documentRepository.countPgVectorExtension() > 0;
            String status = pgvectorAvailable ? "UP" : "DEGRADED";
            return new VectorHealthResponse(status, true, pgvectorAvailable, documentCount, OffsetDateTime.now());
        } catch (Exception unreachable) {
            log.warn("Vector health check failed reason={}", unreachable.getMessage());
            return new VectorHealthResponse("DOWN", false, false, 0, OffsetDateTime.now());
        }
    }

    private void validateVector(List<Float> vector, String label) {
        if (vector == null || vector.isEmpty()) {
            throw VectorException.invalidVector(label + " must not be empty.");
        }
        if (vector.size() != properties.getDimension()) {
            throw VectorException.dimensionMismatch(label + " has " + vector.size()
                    + " dimensions but this service is configured for " + properties.getDimension() + ".");
        }
        for (Float value : vector) {
            if (value == null || value.isNaN() || value.isInfinite()) {
                throw VectorException.invalidVector(label + " contains a null, NaN, or infinite value.");
            }
        }
    }

    private float[] toFloatArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private String toPgVectorLiteral(List<Float> vector) {
        StringBuilder builder = new StringBuilder(vector.size() * 8);
        builder.append('[');
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector.get(i));
        }
        builder.append(']');
        return builder.toString();
    }

    private Map<String, Object> mergeDocumentMetadata(StoreDocumentRequest request) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (request.metadata() != null) {
            merged.putAll(request.metadata());
        }
        merged.put("documentType", request.documentType());
        if (request.source() != null) {
            merged.put("source", request.source());
        }
        merged.put("documentVersion", request.documentVersion());
        return merged;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw VectorException.invalidRequest("filters/metadata could not be serialized: " + e.getOriginalMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private VectorSearchResultItem toResultItem(Object[] row) {
        UUID chunkId = (UUID) row[1];
        UUID documentId = (UUID) row[2];
        String documentKey = (String) row[3];
        int chunkIndex = ((Number) row[4]).intValue();
        String content = (String) row[5];
        Map<String, Object> metadata = parseJson((String) row[6]);
        String provider = (String) row[7];
        String model = (String) row[8];
        double distance = ((Number) row[9]).doubleValue();
        double score = 1.0 - distance;

        return new VectorSearchResultItem(documentId, documentKey, chunkId, chunkIndex, content, score, distance, provider, model, metadata);
    }
}
