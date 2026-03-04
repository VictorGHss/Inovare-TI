package br.dev.ctrls.inovareti.infra.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.exception.FileSizeLimitExceededException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for storing files locally on the server filesystem.
 * Generates a UUID-based filename to prevent conflicts and ensure uniqueness.
 */
@Slf4j
@Service
public class LocalFileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${app.upload.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        this.uploadPath = Paths.get(uploadDir);
        try {
            Files.createDirectories(uploadPath);
            log.info("Upload directory initialized at: {}", uploadPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadPath, e);
        }
    }

    /**
     * Stores a file in the configured directory with a UUID-based filename.
     * @param file the multipart file to store
     * @return the generated stored filename (UUID + extension)
     * @throws IOException if file storage fails
     */
    public String store(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a valid filename");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new FileSizeLimitExceededException("O arquivo excede o limite máximo de 5MB");
        }

        // Extract file extension
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // Generate unique filename using UUID
        String storedFilename = UUID.randomUUID().toString() + extension;
        Path destinationFile = uploadPath.resolve(storedFilename).normalize();

        // Security check: ensure the file is stored within the upload directory
        if (!destinationFile.getParent().equals(uploadPath)) {
            throw new SecurityException("Cannot store file outside upload directory");
        }

        // Copy file to destination
        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
        log.info("File stored: {} -> {}", originalFilename, storedFilename);

        return storedFilename;
    }

    /**
     * Loads a file as a Resource by its stored filename.
     * @param storedFilename the UUID-based filename
     * @return the file as a Resource
     * @throws IOException if file cannot be found or read
     */
    public Resource load(String storedFilename) throws IOException {
        Path file = uploadPath.resolve(storedFilename).normalize();
        
        // Security check
        if (!file.getParent().equals(uploadPath)) {
            throw new SecurityException("Cannot access file outside upload directory");
        }

        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new IOException("File not found or not readable: " + storedFilename);
        }
    }

    /**
     * Deletes a file by its stored filename.
     * @param storedFilename the UUID-based filename
     * @throws IOException if deletion fails
     */
    public void delete(String storedFilename) throws IOException {
        Path file = uploadPath.resolve(storedFilename).normalize();
        
        // Security check
        if (!file.getParent().equals(uploadPath)) {
            throw new SecurityException("Cannot delete file outside upload directory");
        }

        Files.deleteIfExists(file);
        log.info("File deleted: {}", storedFilename);
    }
}
