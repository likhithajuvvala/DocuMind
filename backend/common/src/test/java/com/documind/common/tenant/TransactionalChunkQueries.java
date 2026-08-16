package com.documind.common.tenant;

import com.documind.common.persistence.entity.DocumentChunkEntity;
import com.documind.common.persistence.repository.DocumentChunkRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stands in for how {@code DocumentChunkRepository.findByDocumentId} is actually called in every
 * real call site in this codebase: from inside an explicit {@code @Transactional} service method,
 * never bare. Spring Data JPA only auto-wraps base {@code CrudRepository} methods in their own
 * transaction; a custom derived-query method like this one needs a surrounding transaction for the
 * workspace filter to have anywhere to attach to — see {@link WorkspaceScopedTransactionManager}.
 */
@Component
class TransactionalChunkQueries {

    private final DocumentChunkRepository chunkRepository;

    TransactionalChunkQueries(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Transactional(readOnly = true)
    List<DocumentChunkEntity> findByDocumentId(UUID documentId) {
        return chunkRepository.findByDocumentId(documentId);
    }
}
