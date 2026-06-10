package br.dev.ctrls.inovareti.domain.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço genérico para gerenciamento de armazenamento de arquivos (invoices, notas fiscais).
 * Salva arquivos em disco local e gerencia caminhos e metadados.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXTENSIONS = { "pdf", "jpg", "jpeg", "png" };
    private static final String[] ALLOWED_MIME_TYPES = {
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/jpg"
    };

    /**
     * Salva um arquivo de nota fiscal em disco e retorna os metadados.
     *
     * @param file          Arquivo multipart enviado
     * @param entityId      ID da entidade (Asset ou StockBatch)
     * @param entityType    Tipo da entidade ("asset" ou "batch")
     * @return              {@link InvoiceFileMetadata} com arquivo nome, tipo MIME e caminho
     * @throws BadRequestException se o arquivo for inválido (tamanho, tipo, etc)
     */
    public InvoiceFileMetadata saveInvoiceFile(
            MultipartFile file,
            UUID entityId,
            String entityType) throws BadRequestException {

        // Validações básicas
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Invoice file not provided.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File exceeds maximum size of 5MB.");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new BadRequestException("File name cannot be empty.");
        }

        // Extrai extensão do arquivo
        String fileExtension = getFileExtension(originalFileName).toLowerCase();
        if (!isAllowedExtension(fileExtension)) {
                throw new BadRequestException(
                    "File type not allowed. Accepted: PDF, JPG, PNG.");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !isAllowedMimeType(mimeType)) {
            throw new BadRequestException("MIME type not allowed: " + mimeType);
        }

        validateMagicBytes(file, fileExtension);

        try {
            // Cria diretório se não existir
            Files.createDirectories(Paths.get(uploadDir));

            // Gera nome único para o arquivo usando UUID aleatório
            String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;

            Path filePath = Paths.get(uploadDir, uniqueFileName);

            // Salva o arquivo em disco
            Files.write(filePath, file.getBytes());

            // Define permissões seguras para o arquivo (leitura/escrita apenas, sem execução)
            if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                try {
                    java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = 
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
                    Files.setPosixFilePermissions(filePath, perms);
                } catch (IOException | IllegalArgumentException | UnsupportedOperationException ex) {
                    log.warn("Falha ao definir permissões POSIX para o arquivo salvo: {}", ex.getMessage());
                }
            }

            log.info("File saved successfully: {}", filePath.toString());

            // Retorna metadados
            return InvoiceFileMetadata.builder()
                    .fileName(uniqueFileName)
                    .contentType(mimeType)
                    .filePath(filePath.toString())
                    .build();

        } catch (IOException e) {
            log.error("Error saving invoice file", e);
            throw new BadRequestException("Error saving file: " + e.getMessage());
        }
    }

    /**
     * Remove um arquivo de nota fiscal do disco.
     *
     * @param filePath Caminho do arquivo a ser removido
     */
    public void deleteInvoiceFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("File removed successfully: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Error removing invoice file: {}", filePath, e);
            // Não lança exceção, apenas registra o aviso
        }
    }

    /**
     * Carrega um arquivo de nota fiscal do disco.
     *
     * @param filePath Caminho do arquivo
     * @return         Conteúdo binário do arquivo
     * @throws NotFoundException se o arquivo não existir
     */
    public byte[] loadInvoiceFile(String filePath) throws NotFoundException {
        if (filePath == null || filePath.isBlank()) {
            throw new NotFoundException("File path not provided.");
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new NotFoundException("Invoice file not found: " + filePath);
            }

            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Error loading invoice file: {}", filePath, e);
            throw new NotFoundException("Error loading file: " + e.getMessage());
        }
    }

    /**
     * Extrai a extensão de um nome de arquivo.
     * Exemplo: "invoice.pdf" → "pdf"
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * Verifica se a extensão é permitida.
     */
    private boolean isAllowedExtension(String extension) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se o tipo MIME é permitido.
     */
    private boolean isAllowedMimeType(String mimeType) {
        for (String allowed : ALLOWED_MIME_TYPES) {
            if (allowed.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Valida os magic bytes do arquivo com base na extensão para garantir consistência física.
     */
    private void validateMagicBytes(MultipartFile file, String extension) throws BadRequestException {
        try {
            byte[] headerBytes = new byte[8];
            try (var is = file.getInputStream()) {
                int read = is.read(headerBytes);
                if (read < 4) {
                    throw new BadRequestException("Arquivo muito curto para verificação de cabeçalho.");
                }
            }

            String normalizedExt = extension.toLowerCase();
            switch (normalizedExt) {
                case "pdf" -> {
                    // PDF: 25 50 44 46 (%PDF)
                    if (headerBytes[0] != 0x25 || headerBytes[1] != 0x50 || headerBytes[2] != 0x44 || headerBytes[3] != 0x46) {
                        throw new BadRequestException("Assinatura de arquivo (Magic Bytes) inválida para PDF.");
                    }
                }
                case "png" -> {
                    // PNG: 89 50 4E 47
                    if (headerBytes[0] != (byte) 0x89 || headerBytes[1] != 0x50 || headerBytes[2] != 0x44 || headerBytes[3] != 0x47) {
                        throw new BadRequestException("Assinatura de arquivo (Magic Bytes) inválida para PNG.");
                    }
                }
                case "jpg", "jpeg" -> {
                    // JPEG: FF D8 FF
                    if (headerBytes[0] != (byte) 0xFF || headerBytes[1] != (byte) 0xD8 || headerBytes[2] != (byte) 0xFF) {
                        throw new BadRequestException("Assinatura de arquivo (Magic Bytes) inválida para JPEG.");
                    }
                }
                default -> {}
            }
        } catch (IOException e) {
            throw new BadRequestException("Falha ao ler cabeçalho do arquivo para validação: " + e.getMessage());
        }
    }
}
