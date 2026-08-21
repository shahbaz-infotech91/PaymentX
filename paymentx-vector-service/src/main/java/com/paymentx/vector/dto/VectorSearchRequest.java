package com.paymentx.vector.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * English:
 * The body of POST /api/v1/vector/search - Step 14's exact contract.
 * This service receives an already-computed `queryEmbedding` (Step 14 -
 * "It does NOT generate embeddings"); a caller obtains it from Embedding
 * Service's POST /api/v1/embeddings first. `provider`/`model` identify
 * WHICH embeddings to search against - required, not inferred, because
 * a query vector from one model is not mathematically comparable to
 * embeddings stored under a different model (see
 * AiDocumentEmbedding's "one chunk, multiple embeddings" javadoc) - a
 * search that silently compared across models would return numerically
 * valid but meaningless distances. `topK` is bounded both here
 * (@Max, a fixed API-boundary ceiling) and again against the live
 * configured `vector.max-top-k` in VectorStoreServiceImpl (Step 21 -
 * "Do NOT allow topK = 100000... protect the database from expensive
 * searches"), matching every other bounded-request DTO's two-layer
 * pattern in this platform. `filters` is matched via Postgres JSONB
 * containment against the stored document's metadata (see
 * VectorStoreServiceImpl's javadoc for exactly what gets merged into
 * that metadata at store time) - filtering happens entirely at the
 * database layer, in the same query as the vector search itself (Step
 * 20 - never "retrieve thousands of vectors and filter them in Java").
 * `minScore` is optional (Step 13/21 - "minimum relevance threshold
 * where appropriate") - see VectorSearchResultItem's javadoc for the
 * exact score/distance relationship this threshold is expressed
 * against.
 * Why it exists: Step 14's exact search contract.
 * How it communicates with other components: bound by
 * VectorController.search; consumed by
 * VectorStoreServiceImpl.search -> AiDocumentEmbeddingRepository's
 * native top-K query.
 *
 * Hinglish:
 * POST /api/v1/vector/search ka body - Step 14 ka exact contract. Ye
 * service ek already-computed `queryEmbedding` receive karti hai (Step
 * 14 - "Ye embeddings generate NAHI karti"); ek caller ise pehle
 * Embedding Service ke POST /api/v1/embeddings se leta hai.
 * `provider`/`model` batate hain KAUN se embeddings ke against search
 * karni hai - required hain, inferred nahi, kyunki ek model se aaya
 * query vector ek doosre model ke under stored embeddings ke saath
 * mathematically comparable nahi hai (AiDocumentEmbedding ka "ek chunk,
 * multiple embeddings" javadoc dekho) - ek search jo silently models ke
 * across compare kare numerically valid lekin meaningless distances
 * return karegi. `topK` yahan bhi bounded hai (@Max, ek fixed API-
 * boundary ceiling) aur VectorStoreServiceImpl me live configured
 * `vector.max-top-k` ke against phir se (Step 21 - "topK = 100000 allow
 * mat karo... database ko expensive searches se protect karo"), is
 * platform ke har doosre bounded-request DTO ke two-layer pattern se
 * match karte hue. `filters` Postgres JSONB containment ke through
 * stored document ke metadata ke against match hota hai
 * (VectorStoreServiceImpl ka javadoc dekho exactly kya store time par us
 * metadata me merge hota hai) - filtering poori tarah database layer
 * par hoti hai, vector search wali hi query me (Step 20 - kabhi
 * "hazaaron vectors retrieve karo aur unhe Java me filter karo" nahi).
 * `minScore` optional hai (Step 13/21 - "jahan appropriate ho minimum
 * relevance threshold") - VectorSearchResultItem ka javadoc dekho exact
 * score/distance relationship ke liye jiske against ye threshold
 * express hota hai.
 * Ye kyu hai: Step 14 ka exact search contract.
 * Dusre components se kaise communicate karta hai:
 * VectorController.search ise bind karta hai;
 * VectorStoreServiceImpl.search -> AiDocumentEmbeddingRepository ka
 * native top-K query ise consume karta hai.
 */
public record VectorSearchRequest(

        @NotEmpty(message = "queryEmbedding must not be empty")
        java.util.List<Float> queryEmbedding,

        @Size(max = 64, message = "provider must be at most 64 characters")
        String provider,

        @Size(max = 128, message = "model must be at most 128 characters")
        String model,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 100, message = "topK must be at most 100")
        Integer topK,

        Map<String, Object> filters,

        @DecimalMin(value = "-1.0", message = "minScore must be at least -1.0")
        @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
        Double minScore
) {
}
