package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.input;
import io.micrometer.observation.annotation.Observed;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;


import java.io.IOException;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.infra.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;

/**
 * Controlador para servir arquivos de anexos estÃ¡ticos.
 * Caminho base: /api/attachments
 */
@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Observed
public class FileController {

    private final LocalFileStorageService fileStorageService;
    private final TicketAttachmentRepositoryPort attachmentRepository;
    private final UserRepositoryPort userRepository;

    /**
     * Serve um arquivo pelo nome armazenado.
     * Retorna o arquivo com o content type adequado para exibiÃ§Ã£o inline ou download.
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId;
        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var attachmentOpt = attachmentRepository.findByStoredFilename(filename);
        if (attachmentOpt.isPresent()) {
            var attachment = attachmentOpt.get();
            var ticket = attachment.getTicket();
            
            boolean isOwner = ticket.getRequester().getId().equals(userId);
            boolean isStaff = user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.TECHNICIAN;
            
            if (!isOwner && !isStaff) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        try {
            Resource resource = fileStorageService.load(filename);
            
            // Determina o content type com base na extensÃ£o do arquivo
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


