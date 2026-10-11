package com.lexor.driver.storage;

import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.FileStorageException;
import com.lexor.driver.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class FileSystemDocumentStorageService implements DocumentStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemDocumentStorageService.class);

    private final Path storageLocation;
    private final Set<String> allowedExtensions;

    public FileSystemDocumentStorageService(
            @Value("${lexor.storage.location:uploads/documents}") String location,
            @Value("${lexor.storage.allowed-extensions:pdf,jpg,jpeg,png,webp}") String extensions
    ) {
        this.storageLocation = Paths.get(location).toAbsolutePath().normalize();
        this.allowedExtensions = new HashSet<>(Arrays.asList(extensions.toLowerCase().split(",")));
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.storageLocation);
            logger.info("Driver document storage initialized at: {}", this.storageLocation);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create the document storage directory at: " + this.storageLocation, ex);
        }
    }

    @Override
    public StoredDocument store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        if (originalFilename.contains("..")) {
            throw new BadRequestException("Filename contains invalid relative path sequence: " + originalFilename);
        }

        String extension = getFileExtension(originalFilename);
        if (!allowedExtensions.contains(extension)) {
            throw new BadRequestException("File extension '." + extension + "' is not permitted. Allowed: " + allowedExtensions);
        }

        // Validate Magic Bytes (file content inspection)
        String detectedMimeType = detectAndValidateMimeType(file, extension);

        // Generate safe unique stored filename
        String storedFileName = UUID.randomUUID().toString() + "." + extension;
        Path targetLocation = this.storageLocation.resolve(storedFileName).normalize();

        // Security check: ensure target stays within configured storage directory
        if (!targetLocation.startsWith(this.storageLocation)) {
            throw new BadRequestException("Cannot store file outside current storage directory");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Stored document {} as {}", originalFilename, storedFileName);

            return StoredDocument.builder()
                    .originalFileName(originalFilename)
                    .storedFileName(storedFileName)
                    .storagePath(targetLocation.toString())
                    .contentType(detectedMimeType)
                    .fileSize(file.getSize())
                    .build();
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file " + originalFilename, ex);
        }
    }

    @Override
    public Resource loadAsResource(String filePath) {
        try {
            Path file = Paths.get(filePath).normalize();
            if (!file.isAbsolute()) {
                file = this.storageLocation.resolve(file).normalize();
            }

            // Path traversal verification
            if (!file.startsWith(this.storageLocation) && !Files.exists(file)) {
                throw new BadRequestException("Invalid document path traversal access detected");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Document file not found or is not readable: " + file.getFileName());
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Document file path is invalid: " + filePath);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Path file = Paths.get(filePath).normalize();
            if (!file.isAbsolute()) {
                file = this.storageLocation.resolve(file).normalize();
            }
            if (file.startsWith(this.storageLocation)) {
                Files.deleteIfExists(file);
                logger.debug("Deleted stored file: {}", file);
            }
        } catch (IOException ex) {
            logger.warn("Could not delete stored file: {}", filePath, ex);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private String detectAndValidateMimeType(MultipartFile file, String extension) {
        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                throw new BadRequestException("Uploaded file is too small or corrupt to determine content type");
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not read uploaded file stream", e);
        }

        // PDF signature: %PDF (0x25, 0x50, 0x44, 0x46)
        if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
            if (!extension.equals("pdf")) {
                throw new BadRequestException("File content signature (PDF) does not match file extension ." + extension);
            }
            return "application/pdf";
        }

        // JPEG signature: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            if (!extension.equals("jpg") && !extension.equals("jpeg")) {
                throw new BadRequestException("File content signature (JPEG) does not match file extension ." + extension);
            }
            return "image/jpeg";
        }

        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            if (!extension.equals("png")) {
                throw new BadRequestException("File content signature (PNG) does not match file extension ." + extension);
            }
            return "image/png";
        }

        // WEBP signature: RIFF....WEBP
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            if (!extension.equals("webp")) {
                throw new BadRequestException("File content signature (WEBP) does not match file extension ." + extension);
            }
            return "image/webp";
        }

        throw new BadRequestException("File signature verification failed: file is not a valid PDF, JPEG, PNG, or WEBP document");
    }
}
