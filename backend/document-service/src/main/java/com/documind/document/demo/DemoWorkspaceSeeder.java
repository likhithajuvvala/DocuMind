package com.documind.document.demo;

import com.documind.common.persistence.entity.UserEntity;
import com.documind.common.persistence.repository.UserRepository;
import com.documind.document.upload.DocumentUploadService;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class DemoWorkspaceSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoWorkspaceSeeder.class);
    private static final String MARKDOWN_CONTENT_TYPE = "text/markdown";

    private final DemoWorkspaceProperties properties;
    private final UserRepository userRepository;
    private final DemoWorkspaceRegistrar registrar;
    private final DocumentUploadService uploadService;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public DemoWorkspaceSeeder(
            DemoWorkspaceProperties properties,
            UserRepository userRepository,
            DemoWorkspaceRegistrar registrar,
            DocumentUploadService uploadService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.registrar = registrar;
        this.uploadService = uploadService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnFirstStartup() {
        if (!properties.isEnabled()) {
            return;
        }

        if (userRepository.existsByEmail(properties.getEmail())) {
            LOGGER.info("Demo workspace already exists, skipping seeding");
            return;
        }

        try {
            UserEntity owner = registrar.createWorkspaceOwner(
                    properties.getWorkspaceName(), properties.getEmail(), properties.getPassword());
            int seeded = uploadSampleDocuments(owner);
            LOGGER.info(
                    "Seeded demo workspace {} for {} with {} sample documents",
                    owner.getWorkspaceId(),
                    owner.getEmail(),
                    seeded);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.info("Demo workspace was seeded concurrently by another instance, skipping");
        }
    }

    private int uploadSampleDocuments(UserEntity owner) {
        int seeded = 0;

        for (Resource sample : resolveSamples()) {
            try (InputStream content = sample.getInputStream()) {
                uploadService.ingest(
                        content,
                        sample.contentLength(),
                        sample.getFilename(),
                        MARKDOWN_CONTENT_TYPE,
                        owner.getWorkspaceId(),
                        owner.getId());
                seeded++;
            } catch (IOException exception) {
                LOGGER.warn("Skipping demo document {}", sample.getFilename(), exception);
            }
        }

        return seeded;
    }

    private Resource[] resolveSamples() {
        try {
            return resourceResolver.getResources(properties.getDocumentLocation());
        } catch (IOException exception) {
            LOGGER.warn("No demo documents were found at {}", properties.getDocumentLocation(), exception);
            return new Resource[0];
        }
    }
}
