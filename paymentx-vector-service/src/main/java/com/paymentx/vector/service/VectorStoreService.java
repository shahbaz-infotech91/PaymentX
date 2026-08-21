package com.paymentx.vector.service;

import com.paymentx.vector.dto.StoreDocumentRequest;
import com.paymentx.vector.dto.StoreDocumentResponse;
import com.paymentx.vector.dto.VectorHealthResponse;
import com.paymentx.vector.dto.VectorSearchRequest;
import com.paymentx.vector.dto.VectorSearchResponse;

/**
 * English:
 * The application-layer contract VectorController depends on - matches
 * every other AI Platform service's ThingService interface/impl split
 * exactly: a thin HTTP adapter (VectorController) that never contains
 * business logic, and one real implementation (VectorStoreServiceImpl)
 * that owns validation, upsert semantics, transaction boundaries, and
 * the real repository calls.
 * Why it exists: keeps storage/search/lifecycle logic unit- and
 * integration-testable independent of the HTTP layer.
 * How it communicates with other components: implemented by
 * VectorStoreServiceImpl; injected into VectorController.
 *
 * Hinglish:
 * Wo application-layer contract jispar VectorController depend karta
 * hai - har doosri AI Platform service ke ThingService interface/impl
 * split se exactly match karta hai: ek thin HTTP adapter
 * (VectorController) jisme kabhi business logic nahi hoti, aur ek real
 * implementation (VectorStoreServiceImpl) jo validation, upsert
 * semantics, transaction boundaries, aur real repository calls ki
 * malik hai.
 * Ye kyu hai: storage/search/lifecycle logic ko HTTP layer se
 * independent unit- aur integration-testable rakhta hai.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * dwara implement hota hai; VectorController me inject hota hai.
 */
public interface VectorStoreService {

    StoreDocumentResponse storeDocument(StoreDocumentRequest request);

    void deleteDocument(String documentKey, String documentVersion);

    VectorSearchResponse search(VectorSearchRequest request);

    VectorHealthResponse health();
}
