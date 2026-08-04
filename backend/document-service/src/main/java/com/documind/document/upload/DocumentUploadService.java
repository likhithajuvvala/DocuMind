package com.documind.document.upload;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.security.AuthenticatedUser;
import com.documind.common.storage.ObjectStorage;
import com.documind.common.storage.ObjectStorageException;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final ObjectStorage objectStorage;
    private final UploadValidator uploadValidator;
    private final DocumentEventPublisher eventPublisher;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            ObjectStorage objectStorage,
            UploadValidator uploadValidator,
            DocumentEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.objectStorage = objectStorage;
        this.uploadValidator = uploadValidator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DocumentEntity upload(MultipartFile file, AuthenticatedUser user) {
        uploadValidator.validate(file);

        UUID documentId = UUID.randomUUID();
        String storagePath = buildStoragePath(user.workspaceId(), documentId, file.getOriginalFilename());
        storeContent(file, storagePath);

        Instant now = Instant.now();
        DocumentEntity document = documentRepository.save(new DocumentEntity(
                documentId,
                user.workspaceId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                storagePath,
                DocumentStatus.PENDING,
                user.userId(),
                now));

        eventPublisher.publishUploaded(new DocumentUploadedEvent(
                document.getId(),
                document.getWorkspaceId(),
                document.getUploadedBy(),
                document.getFilename(),
                document.getContentType(),
                document.getStoragePath(),
                now));

        return document;
    }

    @Transactional(readOnly = true)
    public Page<DocumentEntity> listDocuments(UUID workspaceId, Pageable pageable) {
        return documentRepository.findByWorkspaceId(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public DocumentEntity requireDocument(UUID documentId, UUID workspaceId) {
        return documentRepository
                .findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + documentId + " was not found"));
    }

    private void storeContent(MultipartFile file, String storagePath) {
        try {
            objectStorage.store(storagePath, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException exception) {
            throw new ObjectStorageException("Unable to read the uploaded file stream", exception);
        }
    }

    private String buildStoragePath(UUID workspaceId, UUID documentId, String filename) {
        return "%s/%s/%s".formatted(workspaceId, documentId, filename);
    }
}
