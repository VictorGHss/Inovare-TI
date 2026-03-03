package br.dev.ctrls.inovareti.domain.knowledge;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.domain.knowledge.dto.AttachmentResponseDTO;
import br.dev.ctrls.inovareti.infra.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para upload genérico de anexos/imagens.
 * Base path: /api/attachments
 */
@Slf4j
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final LocalFileStorageService fileStorageService;

    /**
     * Faz upload de um arquivo genérico.
     * Protegido para ADMIN e TECHNICIAN apenas.
     * Retorna 201 Created com a URL do arquivo armazenado.
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    public ResponseEntity<AttachmentResponseDTO> uploadFile(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String storedFilename = fileStorageService.store(file);
            String url = "/api/attachments/" + storedFilename;
            
            log.info("File uploaded successfully: {}", storedFilename);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(AttachmentResponseDTO.builder()
                    .url(url)
                    .build());
        } catch (IOException e) {
            log.error("Error uploading file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
