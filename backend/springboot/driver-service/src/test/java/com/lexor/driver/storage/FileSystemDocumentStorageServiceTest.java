package com.lexor.driver.storage;

import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemDocumentStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileSystemDocumentStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new FileSystemDocumentStorageService(tempDir.toString(), "pdf,jpg,jpeg,png,webp");
        storageService.init();
    }

    @Test
    @DisplayName("Should store valid PDF file")
    void testStore_ValidPdf() {
        // PDF header: %PDF
        byte[] pdfBytes = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, 0x00, 0x00 };
        MockMultipartFile file = new MockMultipartFile("file", "test_license.pdf", "application/pdf", pdfBytes);

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("test_license.pdf", stored.getOriginalFileName());
        assertEquals("application/pdf", stored.getContentType());
        assertTrue(stored.getStoredFileName().endsWith(".pdf"));

        Resource loaded = storageService.loadAsResource(stored.getStoragePath());
        assertTrue(loaded.exists());
        assertTrue(loaded.isReadable());
    }

    @Test
    @DisplayName("Should store valid JPEG file")
    void testStore_ValidJpeg() {
        // JPEG header: FF D8 FF
        byte[] jpegBytes = new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01 };
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("image/jpeg", stored.getContentType());
    }

    @Test
    @DisplayName("Should store valid PNG file")
    void testStore_ValidPng() {
        // PNG header: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngBytes = new byte[] { (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D };
        MockMultipartFile file = new MockMultipartFile("file", "card.png", "image/png", pngBytes);

        StoredDocument stored = storageService.store(file);

        assertNotNull(stored);
        assertEquals("image/png", stored.getContentType());
    }

    @Test
    @DisplayName("Should reject empty file upload")
    void testStore_EmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Should reject disallowed file extension (.exe)")
    void testStore_DisallowedExtension() {
        byte[] content = new byte[] { 0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00 };
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", content);
        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Should reject file when content signature does not match extension")
    void testStore_SignatureMismatch() {
        // File named .pdf but containing plain text or wrong bytes
        byte[] textBytes = "This is not a real PDF file".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", textBytes);
        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Should reject path traversal sequences in filename")
    void testStore_PathTraversal() {
        byte[] pdfBytes = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, 0x00, 0x00 };
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", pdfBytes);
        assertThrows(BadRequestException.class, () -> storageService.store(file));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for missing file")
    void testLoadResource_NotFound() {
        assertThrows(ResourceNotFoundException.class, () -> storageService.loadAsResource("non_existent_file.pdf"));
    }

    @Test
    @DisplayName("Should delete stored file safely")
    void testDelete() {
        byte[] pdfBytes = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, 0x00, 0x00 };
        MockMultipartFile file = new MockMultipartFile("file", "to_delete.pdf", "application/pdf", pdfBytes);
        StoredDocument stored = storageService.store(file);

        storageService.delete(stored.getStoragePath());

        assertThrows(ResourceNotFoundException.class, () -> storageService.loadAsResource(stored.getStoragePath()));
    }
}
