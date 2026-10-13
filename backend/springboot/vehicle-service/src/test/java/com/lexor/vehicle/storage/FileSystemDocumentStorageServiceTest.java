package com.lexor.vehicle.storage;

import com.lexor.vehicle.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemDocumentStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileSystemDocumentStorageService storageService;

    // Magic bytes samples
    private static final byte[] PDF_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x35}; // %PDF-1.5
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    private static final byte[] WEBP_BYTES = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @BeforeEach
    void setUp() {
        storageService = new FileSystemDocumentStorageService(tempDir.toString(), "pdf,jpg,jpeg,png,webp");
        storageService.init();
    }

    @Test
    @DisplayName("Successfully store a valid PDF document")
    void testStoreValidPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "rc.pdf", "application/pdf", PDF_BYTES
        );

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("rc.pdf", stored.getOriginalFileName());
        assertEquals("application/pdf", stored.getContentType());
        assertTrue(stored.getStoredFileName().endsWith(".pdf"));
    }

    @Test
    @DisplayName("Successfully store a valid JPEG document")
    void testStoreValidJpeg() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "insurance.jpg", "image/jpeg", JPEG_BYTES
        );

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("image/jpeg", stored.getContentType());
    }

    @Test
    @DisplayName("Successfully store a valid PNG document")
    void testStoreValidPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "puc.png", "image/png", PNG_BYTES
        );

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("image/png", stored.getContentType());
    }

    @Test
    @DisplayName("Successfully store a valid WEBP document")
    void testStoreValidWebp() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "permit.webp", "image/webp", WEBP_BYTES
        );

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("image/webp", stored.getContentType());
    }

    @Test
    @DisplayName("Reject empty upload file")
    void testRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Reject unsupported file extension (e.g. .exe, .sh)")
    void testRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "script.sh", "text/plain", "echo evil".getBytes());

        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Reject file with extension mismatch / invalid magic bytes")
    void testRejectMismatchedSignature() {
        // Plain text disguised as a PDF
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "This is plain text not PDF".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storageService.store(file));
        assertTrue(ex.getMessage().contains("File signature verification failed"));
    }

    @Test
    @DisplayName("Prevent path traversal in original file name")
    void testPreventPathTraversal() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.pdf", "application/pdf", PDF_BYTES
        );

        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Load stored file as resource and delete file")
    void testLoadAndResourceDelete() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", PDF_BYTES);
        StoredDocument stored = storageService.store(file);

        Resource resource = storageService.loadAsResource(stored.getStoragePath());
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());

        storageService.delete(stored.getStoragePath());
        assertFalse(resource.exists());
    }
}
