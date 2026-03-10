package br.dev.ctrls.inovareti.domain.ticket;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.infra.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;

/**
 * Controlador para servir arquivos de anexos estáticos.
 * Caminho base: /api/attachments
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class FileController {

    private final LocalFileStorageService fileStorageService;

    /**
     * Serve um arquivo pelo nome armazenado.
     * Retorna o arquivo com o content type adequado para exibição inline ou download.
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Resource resource = fileStorageService.load(filename);
            
            // Determina o content type com base na extensão do arquivo
            String contentType = "application/octet-stream";
            if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (filename.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (filename.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (filename.toLowerCase().endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (filename.toLowerCase().endsWith(".txt")) {
                contentType = "text/plain";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
