package com.paymentx.rag.context;

import com.paymentx.rag.config.RagProperties;
import com.paymentx.rag.dto.RetrievedChunk;
import com.paymentx.rag.exception.RagException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * English:
 * The dedicated context-assembly step Step 13 requires as its own
 * component, not folded into RagServiceImpl. Responsibilities, in
 * order: (1) deterministic ordering - score descending, then chunkId as
 * a stable tie-break (Step 15 - "do not randomly order retrieved
 * chunks"); (2) exact deduplication by chunkId AND by
 * trim+lowercase+whitespace-collapsed content (Step 16 - "exact
 * normalized-content/chunk ID deduplication is sufficient... do NOT
 * perform expensive semantic deduplication" - no embedding-similarity-
 * based dedup, which would just be a second vector search this phase
 * does not need); (3) a hard cap on chunk COUNT
 * (`rag.max-context-chunks`); (4) a hard cap on total character length
 * (`rag.max-context-characters`) - character-based, not token-based,
 * because no real tokenizer exists anywhere in this platform (Step 14 -
 * "do NOT fabricate token counts" - the same honesty rule
 * AiDocumentChunk.tokenCount already applies in Phase 3.5). WHY hitting
 * the character limit truncates gracefully (stop adding chunks, keep
 * what fits) rather than throwing CONTEXT_TOO_LARGE: a partial-but-real
 * context is more useful to both the LLM and the caller than a hard
 * failure over a budget that exists purely to protect the LLM's context
 * window - CONTEXT_TOO_LARGE is reserved for the one genuine edge case
 * where a SINGLE chunk alone already exceeds the configured maximum
 * (nothing to gracefully drop down to).
 * Why it exists: Step 13's exact dedicated-component requirement -
 * "order retrieved chunks, remove duplicates where appropriate,
 * preserve source metadata, respect context size, format context
 * consistently, prepare citations/evidence."
 * How it communicates with other components: called by RagServiceImpl
 * with the chunks that already passed the relevance threshold; its
 * output feeds both PromptServiceClient.render's `context` variable and
 * RagServiceImpl's RagSource list (only the chunks ContextBuilder
 * actually included, never the ones it dropped for size/duplication).
 *
 * Hinglish:
 * Wo dedicated context-assembly step jo Step 13 ko apne ek component ke
 * roop me chahiye, RagServiceImpl me fold nahi kiya gaya.
 * Responsibilities, order me: (1) deterministic ordering - score
 * descending, phir chunkId ek stable tie-break ke roop me (Step 15 -
 * "retrieved chunks ko randomly order mat karo"); (2) exact
 * deduplication chunkId se AUR trim+lowercase+whitespace-collapsed
 * content se (Step 16 - "exact normalized-content/chunk ID
 * deduplication kaafi hai... expensive semantic deduplication mat
 * karo" - koi embedding-similarity-based dedup nahi, jo bas ek doosri
 * vector search hoti jiski is phase ko zaroorat nahi); (3) chunk COUNT
 * par ek hard cap (`rag.max-context-chunks`); (4) total character
 * length par ek hard cap (`rag.max-context-characters`) - character-
 * based, token-based nahi, kyunki is poore platform me kahin bhi koi
 * real tokenizer exist nahi karta (Step 14 - "token counts fabricate
 * MAT karo" - wahi honesty rule jo AiDocumentChunk.tokenCount already
 * Phase 3.5 me apply karta hai). Character limit hit hone par graceful
 * truncation (chunks add karna band karo, jo fit ho wo rakho) KYU hota
 * hai, CONTEXT_TOO_LARGE throw karne ke bajaye: ek partial-but-real
 * context dono LLM aur caller ke liye ek hard failure se zyada useful
 * hai ek budget ke upar jo purely LLM ke context window ko protect
 * karne ke liye exist karta hai - CONTEXT_TOO_LARGE us ek genuine edge
 * case ke liye reserved hai jahan ek SINGLE chunk akela hi configured
 * maximum se zyada hai (gracefully drop down karne ke liye kuch bacha
 * hi nahi).
 * Ye kyu hai: Step 13 ki exact dedicated-component requirement -
 * "retrieved chunks order karo, jahan appropriate ho duplicates hatao,
 * source metadata preserve karo, context size respect karo, context ko
 * consistently format karo, citations/evidence prepare karo."
 * Dusre components se kaise communicate karta hai: RagServiceImpl ise
 * un chunks ke saath call karta hai jo already relevance threshold pass
 * kar chuke hain; iska output dono ko feed karta hai:
 * PromptServiceClient.render ke `context` variable ko aur
 * RagServiceImpl ki RagSource list ko (sirf wo chunks jo ContextBuilder
 * ne actually include kiye, jo size/duplication ke liye drop kiye wo
 * kabhi nahi).
 */
@Component
public class ContextBuilder {

    private final RagProperties properties;

    public ContextBuilder(RagProperties properties) {
        this.properties = properties;
    }

    public record BuiltContext(String contextText, List<RetrievedChunk> includedChunks) {
    }

    public BuiltContext build(List<RetrievedChunk> relevantChunks) {
        List<RetrievedChunk> ordered = relevantChunks.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed()
                        .thenComparing(chunk -> chunk.chunkId() == null ? "" : chunk.chunkId()))
                .toList();

        List<RetrievedChunk> deduplicated = deduplicate(ordered);

        List<RetrievedChunk> countLimited = deduplicated.size() > properties.getMaxContextChunks()
                ? deduplicated.subList(0, properties.getMaxContextChunks())
                : deduplicated;

        StringBuilder contextText = new StringBuilder();
        List<RetrievedChunk> included = new ArrayList<>();
        int sourceNumber = 1;
        for (RetrievedChunk chunk : countLimited) {
            String formatted = formatChunk(sourceNumber, chunk);
            if (contextText.isEmpty() && formatted.length() > properties.getMaxContextCharacters()) {
                throw RagException.contextTooLarge("A single retrieved chunk (" + formatted.length()
                        + " characters) alone exceeds the configured maximum context size of "
                        + properties.getMaxContextCharacters() + " characters.");
            }
            if (contextText.length() + formatted.length() > properties.getMaxContextCharacters()) {
                break;
            }
            contextText.append(formatted);
            included.add(chunk);
            sourceNumber++;
        }

        return new BuiltContext(contextText.toString(), included);
    }

    private List<RetrievedChunk> deduplicate(List<RetrievedChunk> ordered) {
        Set<String> seenChunkIds = new HashSet<>();
        Set<String> seenNormalizedContent = new HashSet<>();
        List<RetrievedChunk> result = new ArrayList<>(ordered.size());
        for (RetrievedChunk chunk : ordered) {
            String chunkId = chunk.chunkId() == null ? "" : chunk.chunkId();
            String normalizedContent = normalize(chunk.content());
            if (!seenChunkIds.add(chunkId)) {
                continue;
            }
            if (!seenNormalizedContent.add(normalizedContent)) {
                continue;
            }
            result.add(chunk);
        }
        return result;
    }

    private String normalize(String content) {
        return content == null ? "" : content.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String formatChunk(int sourceNumber, RetrievedChunk chunk) {
        String label = chunk.documentKey() != null && !chunk.documentKey().isBlank() ? chunk.documentKey() : chunk.documentId();
        return "[Source " + sourceNumber + ": " + label + "]\n" + chunk.content() + "\n\n";
    }
}
