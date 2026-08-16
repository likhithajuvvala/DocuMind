package com.documind.document.upload;

import com.documind.common.error.UnsupportedDocumentTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class UploadValidator {

    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword",
                    "text/plain",
                    "text/markdown");

    private final Tika tika = new Tika();

    /**
     * Returns the file's real content type as sniffed from its bytes, and rejects it if that type
     * isn't supported. The client-supplied multipart Content-Type header is never consulted here:
     * it's attacker-controlled metadata, not evidence — a renamed executable declared as
     * "application/pdf" would sail straight through a header-only check.
     */
    public String detectAndValidate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new UnsupportedDocumentTypeException("The uploaded file is empty");
        }

        String detectedType = detect(file);
        if (!SUPPORTED_CONTENT_TYPES.contains(detectedType)) {
            throw new UnsupportedDocumentTypeException("Unsupported content type: " + detectedType);
        }
        return detectedType;
    }

    private String detect(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return tika.detect(content, file.getOriginalFilename());
        } catch (IOException exception) {
            throw new UnsupportedDocumentTypeException(
                    "Unable to read the uploaded file to determine its type");
        }
    }
}
