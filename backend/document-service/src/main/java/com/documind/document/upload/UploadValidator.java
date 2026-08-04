package com.documind.document.upload;

import com.documind.common.error.UnsupportedDocumentTypeException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class UploadValidator {

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/markdown");

    public void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new UnsupportedDocumentTypeException("The uploaded file is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedDocumentTypeException("Unsupported content type: " + contentType);
        }
    }
}
