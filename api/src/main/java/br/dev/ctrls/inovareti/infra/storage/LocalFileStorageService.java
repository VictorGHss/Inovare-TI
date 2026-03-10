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
 * Serviço para armazenar arquivos localmente no sistema de arquivos do servidor.
 * Gera um nome de arquivo baseado em UUID para evitar conflitos e garantir unicidade.
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
     * Armazena um arquivo no diretório configurado com um nome baseado em UUID.
     * @param file o arquivo multipart a ser armazenado
     * @return o nome do arquivo gerado (UUID + extensão)
     * @throws IOException se o armazenamento falhar
     */
    public String store(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a valid filename");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new FileSizeLimitExceededException("O arquivo excede o limite máximo de 5MB");
        }

        // Extrai a extensão do arquivo
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // Gera um nome de arquivo único usando UUID
        String storedFilename = UUID.randomUUID().toString() + extension;
        Path destinationFile = uploadPath.resolve(storedFilename).normalize();

        // Verificação de segurança: garante que o arquivo seja armazenado dentro do diretório de upload
        if (!destinationFile.getParent().equals(uploadPath)) {
            throw new SecurityException("Cannot store file outside upload directory");
        }

        // Copia o arquivo para o destino
        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
        log.info("File stored: {} -> {}", originalFilename, storedFilename);

        return storedFilename;
    }

    /**
     * Carrega um arquivo como Resource pelo seu nome armazenado.
     * @param storedFilename o nome do arquivo baseado em UUID
     * @return o arquivo como Resource
     * @throws IOException se o arquivo não puder ser encontrado ou lido
     */
    public Resource load(String storedFilename) throws IOException {
        Path file = uploadPath.resolve(storedFilename).normalize();
        
        // Verificação de segurança
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
     * Exclui um arquivo pelo seu nome armazenado.
     * @param storedFilename o nome do arquivo baseado em UUID
     * @throws IOException se a exclusão falhar
     */
    public void delete(String storedFilename) throws IOException {
        Path file = uploadPath.resolve(storedFilename).normalize();
        
        // Verificação de segurança
        if (!file.getParent().equals(uploadPath)) {
            throw new SecurityException("Cannot delete file outside upload directory");
        }

        Files.deleteIfExists(file);
        log.info("File deleted: {}", storedFilename);
    }
}
