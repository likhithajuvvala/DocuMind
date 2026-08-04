package com.documind.document.upload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.documind.common.error.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadValidatorTest {

    private final UploadValidator validator = new UploadValidator();

    @Test
    void acceptsSupportedContentType() {
        MockMultipartFile file =
                new MockMultipartFile("file", "contract.pdf", "application/pdf", "content".getBytes());

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "content".getBytes());

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(UnsupportedDocumentTypeException.class);
    }
}
