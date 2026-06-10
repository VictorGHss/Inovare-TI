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

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.FileSizeLimitExceededException;
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

    @Value("${app.upload.max-file-size-bytes}")
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
            throw new IllegalArgumentException("File must have a valid name");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new FileSizeLimitExceededException("The file exceeds the maximum allowed size");
        }

        // Extrai a extensão do arquivo
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        // Validação rígida de tipo e magic bytes (PDF, PNG e JPG)
        validateFileTypeAndMagicBytes(file, extension);

        // Gera um nome de arquivo único usando UUID
        String storedFilename = UUID.randomUUID().toString() + extension;
        Path destinationFile = uploadPath.resolve(storedFilename).normalize();

        // Verificação de segurança: garante que o arquivo seja armazenado dentro do diretório de upload
        Path parentDest = destinationFile.getParent();
        if (parentDest == null || !parentDest.equals(uploadPath)) {
            throw new SecurityException("Storing files outside the upload directory is not allowed");
        }

        // Copia o arquivo para o destino
        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

        // Define permissões seguras para o arquivo (leitura/escrita apenas, sem execução)
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            try {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = 
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
                Files.setPosixFilePermissions(destinationFile, perms);
            } catch (IOException | IllegalArgumentException | UnsupportedOperationException ex) {
                log.warn("Falha ao definir permissões POSIX para o arquivo salvo: {}", ex.getMessage());
            }
        }

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
        Path parentLoad = file.getParent();
        if (parentLoad == null || !parentLoad.equals(uploadPath)) {
            throw new SecurityException("Accessing files outside the upload directory is not allowed");
        }

        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new IOException("File not found or inaccessible: " + storedFilename);
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
        Path parentDel = file.getParent();
        if (parentDel == null || !parentDel.equals(uploadPath)) {
            throw new SecurityException("Não é permitido excluir arquivo fora do diretório de upload");
        }

        Files.deleteIfExists(file);
        log.info("File deleted: {}", storedFilename);
    }

    /**
     * Valida de forma rígida a extensão, o Content-Type e os Magic Bytes do arquivo.
     */
    private void validateFileTypeAndMagicBytes(MultipartFile file, String extension) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("O tipo de conteúdo do arquivo (Content-Type) não foi informado.");
        }

        String normalizedExtension = extension.toLowerCase();

        // Valida se a extensão é estritamente uma das seguras permitidas (PDF, PNG, JPG/JPEG)
        if (!normalizedExtension.equals(".pdf") && 
            !normalizedExtension.equals(".png") && 
            !normalizedExtension.equals(".jpg") && 
            !normalizedExtension.equals(".jpeg")) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Apenas PDF, PNG e JPG são aceitos.");
        }

        // Valida o Content-Type associado
        if (normalizedExtension.equals(".pdf") && !contentType.equalsIgnoreCase("application/pdf")) {
            throw new IllegalArgumentException("Content-Type inválido para arquivo PDF.");
        }
        if ((normalizedExtension.equals(".jpg") || normalizedExtension.equals(".jpeg")) && !contentType.equalsIgnoreCase("image/jpeg")) {
            throw new IllegalArgumentException("Content-Type inválido para imagem JPEG.");
        }
        if (normalizedExtension.equals(".png") && !contentType.equalsIgnoreCase("image/png")) {
            throw new IllegalArgumentException("Content-Type inválido para imagem PNG.");
        }

        // Valida os Magic Bytes da assinatura do arquivo
        byte[] headerBytes = new byte[8];
        try (var is = file.getInputStream()) {
            int read = is.read(headerBytes);
            if (read < 4) {
                throw new IllegalArgumentException("Arquivo muito curto para conter cabeçalho válido.");
            }
        }

        switch (normalizedExtension) {
            case ".pdf" -> {
                // PDF: 25 50 44 46 (%PDF)
                if (headerBytes[0] != 0x25 || headerBytes[1] != 0x50 || headerBytes[2] != 0x44 || headerBytes[3] != 0x46) {
                    throw new IllegalArgumentException("Assinatura de arquivo (Magic Bytes) inválida para PDF.");
                }
            }
            case ".png" -> {
                // PNG: 89 50 4E 47 0D 0A 1A 0A
                if (headerBytes[0] != (byte) 0x89 || headerBytes[1] != 0x50 || headerBytes[2] != 0x44 || headerBytes[3] != 0x47) {
                    throw new IllegalArgumentException("Assinatura de arquivo (Magic Bytes) inválida para PNG.");
                }
            }
            case ".jpg", ".jpeg" -> {
                // JPEG: FF D8 FF
                if (headerBytes[0] != (byte) 0xFF || headerBytes[1] != (byte) 0xD8 || headerBytes[2] != (byte) 0xFF) {
                    throw new IllegalArgumentException("Assinatura de arquivo (Magic Bytes) inválida para JPEG.");
                }
            }
            default -> {}
        }
    }
}
