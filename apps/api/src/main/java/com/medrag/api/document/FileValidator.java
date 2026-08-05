package com.medrag.api.document;

import com.medrag.api.config.MedRagProperties;
import com.medrag.api.web.UnprocessableFileException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class FileValidator {
    private static final Set<String> GENERIC_DECLARED_TYPES = Set.of("", MediaType.APPLICATION_OCTET_STREAM_VALUE);
    private static final long MAX_DOCX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 2_000;
    private final MedRagProperties.Uploads uploads;

    public FileValidator(MedRagProperties properties) { this.uploads = properties.uploads(); }

    public ValidatedFile validate(MultipartFile file, byte[] bytes) {
        return validate(file, bytes, uploads.maxBytes(), uploads.allowedMimeTypes());
    }

    public ValidatedFile validate(MultipartFile file, byte[] bytes, long maxBytes, Set<String> allowedMimeTypes) {
        if (bytes.length == 0) throw new UnprocessableFileException("EMPTY_FILE", "File is empty");
        if (bytes.length > maxBytes) throw new UnprocessableFileException("FILE_TOO_LARGE", "File exceeds configured tenant limit");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String detected = detect(bytes, extension);
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!allowedMimeTypes.contains(detected))
            throw new UnprocessableFileException("UNSUPPORTED_FILE_TYPE", "Only PDF, DOCX, and UTF-8 TXT are accepted");
        if (!GENERIC_DECLARED_TYPES.contains(declared) && !declared.equals(detected))
            throw new UnprocessableFileException("MIME_TYPE_MISMATCH", "Declared file type does not match content");
        if (!matches(extension, detected))
            throw new UnprocessableFileException("FILE_TYPE_MISMATCH", "File extension does not match content");
        return new ValidatedFile(extension, detected);
    }

    private String detect(byte[] bytes, String extension) {
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-')
            return "application/pdf";
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4 && isSafeDocx(bytes))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (extension.equals("txt") && isUtf8Text(bytes)) return "text/plain";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private boolean isSafeDocx(byte[] bytes) {
        boolean contentTypes = false, documentXml = false;
        long total = 0; int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_DOCX_ENTRIES) throw new UnprocessableFileException("DOCX_ARCHIVE_LIMIT", "DOCX contains too many entries");
                String normalized = entry.getName().replace('\\', '/');
                if (normalized.startsWith("/") || normalized.contains("../"))
                    throw new UnprocessableFileException("DOCX_PATH_TRAVERSAL", "DOCX contains an unsafe archive path");
                if (normalized.equals("[Content_Types].xml")) contentTypes = true;
                if (normalized.equals("word/document.xml")) documentXml = true;
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_DOCX_UNCOMPRESSED_BYTES)
                        throw new UnprocessableFileException("DOCX_ARCHIVE_LIMIT", "DOCX expands beyond the safety limit");
                }
            }
            return contentTypes && documentXml;
        } catch (UnprocessableFileException e) { throw e; }
        catch (Exception e) { return false; }
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) { return false; }
    }

    private boolean matches(String ext, String mime) {
        return (ext.equals("pdf") && mime.equals("application/pdf"))
                || (ext.equals("docx") && mime.contains("wordprocessingml"))
                || (ext.equals("txt") && mime.equals("text/plain"));
    }

    public record ValidatedFile(String extension, String contentType) {}
}
