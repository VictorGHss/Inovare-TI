package br.dev.ctrls.inovareti.domain.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.config.DatabaseBackupScheduler;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.infra.security.TwoFactorSessionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador administrativo para gerenciamento de backups físicos do sistema.
 * Restrito a usuários com privilégios de ADMINISTRADOR e exige atestação ativa de 2FA.
 */
@RestController
@RequestMapping("/admin/backups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class BackupController {

    private final DatabaseBackupScheduler databaseBackupScheduler;
    private final TwoFactorSessionGuard twoFactorSessionGuard;

    @Value("${app.backup.temp-dir}")
    private String tempDir;

    /**
     * Lista todos os backups compactados (.zip) disponíveis na pasta de backups.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<BackupInfoDTO>> listBackups() {
        // Exige validação ativa do segundo fator de autenticação (MFA/2FA) antes de listar os backups.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);

        log.info("Solicitação recebida para listar todos os backups de banco de dados na pasta: {}", tempDir);
        File folder = new File(tempDir);
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        List<BackupInfoDTO> dtos = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    LocalDateTime lastModified = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
                    dtos.add(new BackupInfoDTO(
                            file.getName(),
                            file.length(),
                            lastModified
                    ));
                }
            }
        }

        // Ordena por data de modificação decrescente (mais recentes primeiro)
        dtos.sort((a, b) -> b.lastModified().compareTo(a.lastModified()));
        return ResponseEntity.ok(dtos);
    }

    /**
     * Dispara manualmente a geração de um novo backup.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/trigger")
    public ResponseEntity<Void> triggerBackup() {
        // Exige validação ativa do segundo fator de autenticação (MFA/2FA) antes de disparar manualmente um backup.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);

        log.info("Execução manual de backup disparada pelo administrador.");
        try {
            databaseBackupScheduler.executeBackupManual();
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception ex) {
            log.error("Falha ao executar backup manual", ex);
            throw new BadRequestException("Falha ao gerar o backup manualmente: " + ex.getMessage());
        }
    }

    /**
     * Faz o download de um arquivo de backup específico pelo nome.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        // Exige validação ativa do segundo fator de autenticação (MFA/2FA) antes de baixar um backup.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);

        log.info("Solicitação recebida para baixar o arquivo de backup: {}", filename);
        
        // Proteção simples contra Path Traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Nome do arquivo de backup inválido.");
        }

        File file = new File(tempDir, filename);
        if (!file.exists() || !file.isFile()) {
            throw new NotFoundException("Arquivo de backup '" + filename + "' não encontrado.");
        }

        Resource resource = new FileSystemResource(file);
        
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");

        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "application/zip";
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (IOException e) {
            log.error("Falha ao determinar tipo de conteúdo para download do arquivo de backup", e);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }
    }

    /**
     * Exclui um arquivo de backup específico pelo nome.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteBackup(@PathVariable String filename) {
        // Exige validação ativa do segundo fator de autenticação (MFA/2FA) antes de excluir um backup.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);

        log.info("Solicitação recebida para excluir o arquivo de backup: {}", filename);

        // Proteção simples contra Path Traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Nome do arquivo de backup inválido.");
        }

        File file = new File(tempDir, filename);
        if (!file.exists() || !file.isFile()) {
            throw new NotFoundException("Arquivo de backup '" + filename + "' não encontrado.");
        }

        if (file.delete()) {
            log.info("Arquivo de backup excluído com sucesso: {}", filename);
            return ResponseEntity.noContent().build();
        } else {
            log.error("Falha ao excluir o arquivo de backup: {}", filename);
            throw new BadRequestException("Não foi possível excluir o arquivo de backup '" + filename + "'.");
        }
    }

    /**
     * DTO contendo detalhes de um backup.
     */
    public record BackupInfoDTO(
            String filename,
            long sizeBytes,
            LocalDateTime lastModified
    ) {}
}
