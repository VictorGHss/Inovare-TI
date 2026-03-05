package br.dev.ctrls.inovareti.domain.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
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

    @Value("${file.upload.dir:uploads/invoices}")
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
            throw new BadRequestException("Arquivo de nota fiscal não enviado.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Arquivo excede o tamanho máximo de 5MB.");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new BadRequestException("Nome do arquivo não pode ser vazio.");
        }

        // Extrai extensão do arquivo
        String fileExtension = getFileExtension(originalFileName).toLowerCase();
        if (!isAllowedExtension(fileExtension)) {
            throw new BadRequestException(
                    "Tipo de arquivo não permitido. Arquivos aceitos: PDF, JPG, PNG.");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !isAllowedMimeType(mimeType)) {
            throw new BadRequestException("Tipo MIME não permitido: " + mimeType);
        }

        try {
            // Cria diretório se não existir
            Files.createDirectories(Paths.get(uploadDir));

            // Gera nome único para o arquivo
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String uniqueFileName = String.format("%s-%s-%s.%s",
                    entityType,
                    entityId.toString(),
                    timestamp,
                    fileExtension);

            Path filePath = Paths.get(uploadDir, uniqueFileName);

            // Salva o arquivo em disco
            Files.write(filePath, file.getBytes());
            log.info("Arquivo salvo com sucesso: {}", filePath.toString());

            // Retorna metadados
            return InvoiceFileMetadata.builder()
                    .fileName(uniqueFileName)
                    .contentType(mimeType)
                    .filePath(filePath.toString())
                    .build();

        } catch (IOException e) {
            log.error("Erro ao salvar arquivo de nota fiscal", e);
            throw new BadRequestException("Erro ao salvar arquivo: " + e.getMessage());
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
                log.info("Arquivo removido com sucesso: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Erro ao remover arquivo de nota fiscal: {}", filePath, e);
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
            throw new NotFoundException("Caminho do arquivo não fornecido.");
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new NotFoundException("Arquivo de nota fiscal não encontrado: " + filePath);
            }

            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Erro ao carregar arquivo de nota fiscal: {}", filePath, e);
            throw new NotFoundException("Erro ao carregar arquivo: " + e.getMessage());
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
}
