package com.documind.document.api;

import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.repository.IngestionJobRepository;
import com.documind.common.security.AuthenticatedUser;
import com.documind.common.security.CurrentUser;
import com.documind.document.api.dto.DocumentStatusResponse;
import com.documind.document.api.dto.DocumentSummaryResponse;
import com.documind.document.lifecycle.DocumentLifecycleService;
import com.documind.document.upload.DocumentUploadService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService uploadService;
    private final DocumentLifecycleService lifecycleService;
    private final IngestionJobRepository ingestionJobRepository;

    public DocumentController(
            DocumentUploadService uploadService,
            DocumentLifecycleService lifecycleService,
            IngestionJobRepository ingestionJobRepository) {
        this.uploadService = uploadService;
        this.lifecycleService = lifecycleService;
        this.ingestionJobRepository = ingestionJobRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentSummaryResponse> upload(@RequestParam("file") MultipartFile file) {
        AuthenticatedUser user = CurrentUser.require();
        DocumentEntity document = uploadService.upload(file, user);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentSummaryResponse.from(document));
    }

    @GetMapping
    public Page<DocumentSummaryResponse> listDocuments(@PageableDefault(size = 20) Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        return uploadService.listDocuments(user.workspaceId(), pageable).map(DocumentSummaryResponse::from);
    }

    @GetMapping("/{documentId}/status")
    public DocumentStatusResponse documentStatus(@PathVariable UUID documentId) {
        AuthenticatedUser user = CurrentUser.require();
        DocumentEntity document = uploadService.requireDocument(documentId, user.workspaceId());
        return ingestionJobRepository
                .findFirstByDocumentIdOrderByStartedAtDesc(documentId)
                .map(job -> toStatusResponse(document, job))
                .orElseGet(() -> new DocumentStatusResponse(
                        document.getId(), document.getStatus(), null, 0, null, null, null));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        AuthenticatedUser user = CurrentUser.require();
        lifecycleService.delete(documentId, user.workspaceId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{documentId}/reindex")
    public ResponseEntity<DocumentSummaryResponse> reindexDocument(@PathVariable UUID documentId) {
        AuthenticatedUser user = CurrentUser.require();
        DocumentEntity document = lifecycleService.reindex(documentId, user.workspaceId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentSummaryResponse.from(document));
    }

    private DocumentStatusResponse toStatusResponse(DocumentEntity document, IngestionJobEntity job) {
        return new DocumentStatusResponse(
                document.getId(),
                document.getStatus(),
                job.getStatus(),
                job.getChunkCount(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
