package com.paymentx.rag.dto;

/**
 * English:
 * The real, honest outcome of one RAG query - three distinct, truthful
 * states instead of a single success/failure boolean that would
 * misrepresent "the knowledge base genuinely has nothing relevant" as
 * either a fabricated answer or an error. SUCCESS: relevant context was
 * found and the LLM produced a real, grounded answer.
 * INSUFFICIENT_CONTEXT (Step 22 - the CRITICAL PRINCIPLE's core
 * requirement): no retrieved chunk passed the configured relevance
 * threshold (or Vector Service returned zero results) - the LLM is
 * NEVER called in this case, `answer` is a real, honest, hardcoded
 * explanation string, never an invented one. REFUSED: relevant context
 * WAS found and passed to the LLM, but the LLM itself declined to
 * answer (LLM Service's own `refused` field, passed through) - distinct
 * from INSUFFICIENT_CONTEXT because the failure mode is different (a
 * model policy decision, not a retrieval gap) and a caller/operator
 * needs to be able to tell them apart.
 * Why it exists: the CRITICAL PRINCIPLE at the top of the Phase 3.6
 * brief - "If sufficient relevant information cannot be retrieved: DO
 * NOT invent an answer. Return a truthful response indicating
 * insufficient knowledge/evidence" - made impossible to violate by
 * construction, the same way AiComponentStatus (Phase 3.1) and
 * GenerateResponse.refused (Phase 3.3) already make their own honesty
 * rules structural rather than a matter of remembering not to fabricate.
 * How it communicates with other components: serialized by Jackson as
 * a plain string inside RagQueryResponse.status.
 *
 * Hinglish:
 * Ek RAG query ka real, honest outcome - ek single success/failure
 * boolean ke bajaye teen alag, truthful states jo "knowledge base ke
 * paas genuinely kuch relevant nahi hai" ko ya toh ek fabricated answer
 * ya ek error jaisa misrepresent kar deta. SUCCESS: relevant context
 * mila aur LLM ne ek real, grounded answer produce kiya.
 * INSUFFICIENT_CONTEXT (Step 22 - CRITICAL PRINCIPLE ki core
 * requirement): koi retrieved chunk configured relevance threshold pass
 * nahi hua (ya Vector Service ne zero results return kiye) - is case me
 * LLM KABHI call nahi hota, `answer` ek real, honest, hardcoded
 * explanation string hai, kabhi ek invented wala nahi. REFUSED: relevant
 * context MILA tha aur LLM ko pass kiya gaya, lekin LLM ne khud jawab
 * dene se mana kar diya (LLM Service ka apna `refused` field, pass
 * through kiya gaya) - INSUFFICIENT_CONTEXT se alag hai kyunki failure
 * mode alag hai (ek model policy decision, ek retrieval gap nahi) aur
 * ek caller/operator ko unhe alag bata sakna chahiye.
 * Ye kyu hai: Phase 3.6 brief ke top ka CRITICAL PRINCIPLE - "Agar
 * sufficient relevant information retrieve nahi ho sakti: ek answer
 * INVENT MAT karo. Ek truthful response return karo jo insufficient
 * knowledge/evidence indicate kare" - construction se hi violate karna
 * impossible bana diya gaya, wahi tarike se jaise AiComponentStatus
 * (Phase 3.1) aur GenerateResponse.refused (Phase 3.3) already apne
 * honesty rules ko structural banate hain, na ki sirf fabricate na
 * karne yaad rakhne ki baat.
 * Dusre components se kaise communicate karta hai: Jackson isse
 * RagQueryResponse.status ke andar ek plain string ke roop me
 * serialize karta hai.
 */
public enum RagQueryStatus {
    SUCCESS,
    INSUFFICIENT_CONTEXT,
    REFUSED
}
