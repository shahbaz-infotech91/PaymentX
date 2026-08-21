package com.paymentx.rag.context;

import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.dto.RetrievedChunk;
import com.paymentx.rag.exception.RagErrorCodes;
import com.paymentx.rag.exception.RagException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Pure unit tests for ContextBuilder - no mocks, no HTTP, no database;
 * every case here is a deterministic function of its input list, which
 * is exactly why this logic was pulled into its own dedicated component
 * (Step 13) instead of being buried inside RagServiceImpl's orchestration.
 * What it verifies: deterministic ordering (score descending, stable
 * chunkId tie-break - Step 15); exact chunkId and normalized-content
 * deduplication (Step 16); a hard chunk-count ceiling; graceful
 * character-limit truncation (keeps what fits, does not error); the one
 * real CONTEXT_TOO_LARGE edge case (a single chunk alone exceeding the
 * configured maximum).
 * Why it exists: Step 37 items 9/10 - context size limit, source
 * ordering/deduplication.
 * How it communicates with other components: exercises ContextBuilder
 * directly.
 *
 * Hinglish:
 * ContextBuilder ke liye pure unit tests - koi mocks nahi, koi HTTP
 * nahi, koi database nahi; yahan har case apne input list ka ek
 * deterministic function hai, yehi exact reason hai ki ye logic apne
 * ek dedicated component me pull kiya gaya (Step 13) RagServiceImpl ke
 * orchestration ke andar bury karne ke bajaye. Ye kya verify karta hai:
 * deterministic ordering (score descending, stable chunkId tie-break -
 * Step 15); exact chunkId aur normalized-content deduplication (Step
 * 16); ek hard chunk-count ceiling; graceful character-limit truncation
 * (jo fit ho wo rakhta hai, error nahi karta); ek real CONTEXT_TOO_LARGE
 * edge case (ek single chunk akela configured maximum se zyada).
 * Ye kyu hai: Step 37 items 9/10 - context size limit, source
 * ordering/deduplication.
 * Dusre components se kaise communicate karta hai: ContextBuilder ko
 * seedhe exercise karta hai.
 */
class ContextBuilderTest {

    private RagProperties properties;
    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setMaxContextChunks(5);
        properties.setMaxContextCharacters(1000);
        contextBuilder = new ContextBuilder(properties);
    }

    private static RetrievedChunk chunk(String chunkId, String content, double score) {
        return new RetrievedChunk("doc-1", chunkId, "doc-key-1", content, score, 1.0 - score);
    }

    @Test
    void build_ordersByScoreDescending() {
        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(
                chunk("c1", "low relevance", 0.5),
                chunk("c2", "high relevance", 0.9),
                chunk("c3", "mid relevance", 0.7)));

        assertThat(result.includedChunks()).extracting(RetrievedChunk::chunkId).containsExactly("c2", "c3", "c1");
    }

    @Test
    void build_tiedScores_breaksTiesByChunkIdForDeterminism() {
        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(
                chunk("c-zebra", "content z", 0.8),
                chunk("c-alpha", "content a", 0.8)));

        assertThat(result.includedChunks()).extracting(RetrievedChunk::chunkId).containsExactly("c-alpha", "c-zebra");
    }

    @Test
    void build_duplicateChunkIds_keepsOnlyFirstOccurrence() {
        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(
                chunk("c1", "content", 0.9),
                chunk("c1", "content", 0.9)));

        assertThat(result.includedChunks()).hasSize(1);
    }

    @Test
    void build_duplicateNormalizedContentDifferentChunkIds_dedupesByContent() {
        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(
                chunk("c1", "Duplicate   payment   detected.", 0.9),
                chunk("c2", "duplicate payment detected.", 0.8)));

        assertThat(result.includedChunks()).hasSize(1);
        assertThat(result.includedChunks().get(0).chunkId()).isEqualTo("c1");
    }

    @Test
    void build_moreChunksThanMaxContextChunks_limitsToConfiguredCount() {
        properties.setMaxContextChunks(2);
        properties.setMaxContextCharacters(10_000);

        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(
                chunk("c1", "first", 0.9),
                chunk("c2", "second", 0.8),
                chunk("c3", "third", 0.7)));

        assertThat(result.includedChunks()).hasSize(2);
    }

    @Test
    void build_totalSizeExceedsCharacterLimit_truncatesGracefullyWithoutError() {
        properties.setMaxContextChunks(10);
        // Each formatted chunk is content (30 chars) + the "[Source N: doc-key-1]\n...\n\n" wrapper
        // (~24 chars) = ~54 chars - 120 comfortably fits the first chunk alone (proving graceful
        // truncation, not the single-chunk-too-large edge case) but not all three (~162 chars).
        properties.setMaxContextCharacters(120);

        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(
                chunk("c1", "a".repeat(30), 0.9),
                chunk("c2", "b".repeat(30), 0.8),
                chunk("c3", "c".repeat(30), 0.7)));

        assertThat(result.includedChunks().size()).isLessThan(3);
        assertThat(result.includedChunks()).isNotEmpty();
        assertThat(result.contextText().length()).isLessThanOrEqualTo(120);
    }

    @Test
    void build_singleChunkAloneExceedsMaxCharacters_throwsContextTooLarge() {
        properties.setMaxContextCharacters(20);

        assertThatThrownBy(() -> contextBuilder.build(List.of(chunk("c1", "x".repeat(100), 0.9))))
                .isInstanceOf(RagException.class)
                .satisfies(ex -> assertThat(((RagException) ex).getErrorCode()).isEqualTo(RagErrorCodes.CONTEXT_TOO_LARGE));
    }

    @Test
    void build_formattedContextIncludesSourceLabelAndContent() {
        ContextBuilder.BuiltContext result = contextBuilder.build(List.of(chunk("c1", "Duplicate payment detected.", 0.9)));

        assertThat(result.contextText()).contains("doc-key-1");
        assertThat(result.contextText()).contains("Duplicate payment detected.");
    }
}
