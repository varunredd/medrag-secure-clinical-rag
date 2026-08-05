package com.medrag.api.document;

import com.medrag.api.config.MedRagProperties;
import com.medrag.api.web.UnprocessableFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {
    private final FileValidator validator = new FileValidator(new MedRagProperties(
            null,
            null,
            null,
            new MedRagProperties.Uploads(
                    1024 * 1024,
                    Set.of(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain"
                    )
            ),
            null,
            null
    ));

    @Test
    void acceptsStrictUtf8Text() {
        byte[] body = "Medication reviewed on 2026-08-04".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "record.txt", "text/plain", body);

        FileValidator.ValidatedFile validated = validator.validate(file, body);

        assertThat(validated.extension()).isEqualTo("txt");
        assertThat(validated.contentType()).isEqualTo("text/plain");
    }

    @Test
    void rejectsExtensionAndContentMismatch() {
        byte[] body = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "record.txt", "text/plain", body);

        assertThatThrownBy(() -> validator.validate(file, body))
                .isInstanceOf(UnprocessableFileException.class)
                .hasMessageContaining("Declared file type");
    }
}
