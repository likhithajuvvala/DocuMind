package com.documind.ingestion.pipeline;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.domain.IngestionStatus;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.IngestionJobRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionJobTracker {

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository jobRepository;

    public IngestionJobTracker(DocumentRepository documentRepository, IngestionJobRepository jobRepository) {
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public IngestionJobEntity start(DocumentEntity document) {
        document.changeStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        return jobRepository.save(new IngestionJobEntity(
                UUID.randomUUID(),
                document.getId(),
                document.getWorkspaceId(),
                IngestionStatus.QUEUED,
                Instant.now()));
    }

    @Transactional
    public void advance(IngestionJobEntity job, IngestionStatus status) {
        job.advanceTo(status);
        jobRepository.save(job);
    }

    @Transactional
    public void complete(DocumentEntity document, IngestionJobEntity job, int chunkCount) {
        job.complete(chunkCount, Instant.now());
        jobRepository.save(job);
        document.changeStatus(DocumentStatus.INDEXED);
        documentRepository.save(document);
    }

    @Transactional
    public void fail(DocumentEntity document, IngestionJobEntity job, String reason) {
        job.fail(reason, Instant.now());
        jobRepository.save(job);
        document.changeStatus(DocumentStatus.FAILED);
        documentRepository.save(document);
    }
}
