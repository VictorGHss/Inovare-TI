package br.dev.ctrls.inovareti.domain.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador para operações administrativas.
 * Caminho base: /api/admin
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final CsvImportService csvImportService;

    /**
     * Importação em massa de usuários e equipamentos a partir de arquivo CSV.
     * Formato CSV: UserName;UserEmail;UserRole;SectorName;AssetName;AssetCategory;PatrimonyCode;AssetSpecs
     *
     * @param file arquivo CSV
     * @return resultado da importação com estatísticas
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import/csv")
    public ResponseEntity<ImportResultDTO> importCsv(
            @RequestParam("file") MultipartFile file) {
        
        log.info("Received CSV import request. File: {}, Size: {} bytes", 
                file.getOriginalFilename(), file.getSize());
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResult("Arquivo CSV está vazio"));
        }
        
        if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(createErrorResult("Apenas arquivos CSV são aceitos"));
        }
        
        try {
            ImportResultDTO result = csvImportService.importCsv(file);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(result);
            }
            
        } catch (Exception e) {
            log.error("Error processing CSV import", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResult("Erro ao processar arquivo: " + e.getMessage()));
        }
    }
    
    private ImportResultDTO createErrorResult(String errorMessage) {
        ImportResultDTO result = new ImportResultDTO();
        result.setSuccess(false);
        result.getErrors().add(errorMessage);
        return result;
    }
}
