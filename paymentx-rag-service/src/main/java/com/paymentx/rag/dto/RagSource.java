package com.paymentx.rag.dto;

/**
 * English:
 * One real piece of evidence behind a grounded answer - Step 21's exact
 * "enough information to identify the knowledge used" requirement,
 * deliberately narrow: `documentId`/`chunkId` (real Vector Service
 * identifiers, needed so a caller/operator can look up the exact source
 * row), `source` (a human-readable label - the document's real
 * `documentKey`/name from Vector Service, not a database internal), and
 * `score` (the same real cosine-similarity value RagServiceImpl
 * computed when applying the relevance threshold - see
 * VectorSearchResultItem's own javadoc in paymentx-vector-service for
 * the exact score/distance math this value inherits). Deliberately does
 * NOT include the chunk's raw embedding vector (Step 6/21 - never
 * expose raw embeddings) or any database/filesystem/credential detail.
 * Why it exists: Step 21's citation requirement - a caller must be able
 * to see WHAT knowledge grounded an answer, not just trust the text.
 * How it communicates with other components: built by RagServiceImpl
 * from the context chunks ContextBuilder actually included in the
 * final prompt (not from the raw, pre-threshold/pre-dedup search
 * results - a source list that included a chunk the LLM never actually
 * saw would misrepresent the answer's real evidentiary basis); nested
 * inside RagQueryResponse.sources.
 *
 * Hinglish:
 * Ek grounded answer ke peeche ek real evidence - Step 21 ki exact
 * "us knowledge ko identify karne layak kaafi information jo use hui"
 * requirement, jaan-boojh kar narrow: `documentId`/`chunkId` (real
 * Vector Service identifiers, chahiye taaki ek caller/operator exact
 * source row lookup kar sake), `source` (ek human-readable label - us
 * document ka real `documentKey`/name Vector Service se, ek database
 * internal nahi), aur `score` (wahi real cosine-similarity value jo
 * RagServiceImpl ne relevance threshold apply karte waqt compute kiya
 * tha - exact score/distance math ke liye paymentx-vector-service me
 * VectorSearchResultItem ka apna javadoc dekho jisse ye value inherit
 * hoti hai). Jaan-boojh kar chunk ka raw embedding vector include NAHI
 * karta (Step 6/21 - raw embeddings kabhi expose mat karo) ya koi bhi
 * database/filesystem/credential detail.
 * Ye kyu hai: Step 21 ki citation requirement - ek caller ko dekh sakna
 * chahiye ki ek answer ko KAUN si knowledge ne ground kiya, sirf text
 * par trust nahi karna chahiye.
 * Dusre components se kaise communicate karta hai: RagServiceImpl ise
 * un context chunks se banata hai jo ContextBuilder ne actually final
 * prompt me include kiye (raw, pre-threshold/pre-dedup search results
 * se nahi - ek source list jisme ek chunk shamil ho jise LLM ne
 * actually kabhi dekha hi nahi wo answer ka real evidentiary basis
 * misrepresent karegi); RagQueryResponse.sources ke andar nested hai.
 */
public record RagSource(
        String documentId,
        String chunkId,
        String source,
        double score
) {
}
