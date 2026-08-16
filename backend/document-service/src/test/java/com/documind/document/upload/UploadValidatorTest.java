package com.documind.document.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.documind.common.error.UnsupportedDocumentTypeException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadValidatorTest {

    private final UploadValidator validator = new UploadValidator();

    @Test
    void detectsAPdfFromItsMagicBytesRegardlessOfTheDeclaredContentType() {
        // Declared as something else entirely: sniffing must still recognise the real %PDF- magic.
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "contract.pdf",
                        "image/png",
                        "%PDF-1.4\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(validator.detectAndValidate(file)).isEqualTo("application/pdf");
    }

    @Test
    void rejectsAnExecutableDisguisedWithAPdfContentTypeAndFilename() {
        // The classic spoofing attempt this validator exists to stop: a Windows PE binary (MZ
        // magic) declared by the client as a PDF, with a .pdf filename to match.
        byte[] peHeader = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "totally-a-contract.pdf", "application/pdf", peHeader);

        assertThatThrownBy(() -> validator.detectAndValidate(file))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void acceptsPlainTextEvenWithNoDeclaredContentType() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "notes.txt",
                        null,
                        "just plain notes".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.detectAndValidate(file)).isEqualTo("text/plain");
    }

    @Test
    void detectsMarkdownFromTheFilenameExtensionSinceItsContentIsPlainText() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "readme.md",
                        "application/octet-stream",
                        "# Heading\n\nBody text.".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.detectAndValidate(file)).isEqualTo("text/markdown");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file =
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.detectAndValidate(file))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }
}
