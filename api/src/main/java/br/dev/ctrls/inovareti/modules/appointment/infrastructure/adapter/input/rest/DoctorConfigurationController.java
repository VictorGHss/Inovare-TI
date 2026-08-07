package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para expor endpoints de gerenciamento das configurações dos médicos.
 * Permite realizar operações CRUD dinamicamente no painel de administração (front-end).
 */
@Slf4j
@RestController
@RequestMapping("/v1/doctors/configurations")
@RequiredArgsConstructor
public class DoctorConfigurationController {

    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService blipNotificationService;

    /**
     * Endpoint de teste para disparo manual da avaliação Google Review.
     */
    @PostMapping("/test-review")
    public ResponseEntity<java.util.Map<String, Object>> testGoogleReview(
            @org.springframework.web.bind.annotation.RequestParam String phone,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "Paciente Teste") String patientName,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "Dr. Eduardo Bisinella") String doctorName,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "jrskH337hFK5Mn3WP") String googleReviewHash) {
        
        log.info("[REST] Teste manual de disparo de avaliação Google para o telefone={}, paciente={}, medico={}, hash={}",
                phone, patientName, doctorName, googleReviewHash);
        
        blipNotificationService.sendReviewTemplateMessage(phone, "pesquisa_avaliacao_google_v1_copia", patientName, doctorName, googleReviewHash);
        
        return ResponseEntity.ok(java.util.Map.of(
            "status", "success",
            "message", "Template pesquisa_avaliacao_google_v1_copia disparado com sucesso!",
            "phone", phone,
            "patientName", patientName,
            "doctorName", doctorName,
            "googleReviewHash", googleReviewHash
        ));
    }

    /**
     * Salva ou atualiza a configuração de um médico.
     *
     * @param config Dados da configuração a ser persistida.
     * @return A configuração salva com status 201 Created.
     */
    @PostMapping
    public ResponseEntity<DoctorConfiguration> save(@RequestBody DoctorConfiguration config) {
        log.info("[REST] Salvando configuração do profissional ID: {}. Nome: {}", 
                config.getFeegowProfissionalId(), config.getDoctorName());
        DoctorConfiguration saved = doctorConfigurationRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Lista todas as configurações de médicos registradas.
     *
     * @return Lista de configurações.
     */
    @GetMapping
    public ResponseEntity<List<DoctorConfiguration>> findAll() {
        log.info("[REST] Listando todas as configurações de médicos.");
        List<DoctorConfiguration> list = doctorConfigurationRepository.findAll();
        return ResponseEntity.ok(list);
    }

    /**
     * Busca a configuração de um médico pelo ID.
     *
     * @param id ID do profissional Feegow.
     * @return Configuração do médico ou 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DoctorConfiguration> findById(@PathVariable Long id) {
        log.info("[REST] Buscando configuração para o profissional ID: {}", id);
        return doctorConfigurationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Salva ou atualiza a configuração de um médico em lote.
     *
     * @param configs Lista de configurações a serem persistidas.
     * @return Lista das configurações salvas com status 200 OK.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<DoctorConfiguration>> saveBatch(@RequestBody List<DoctorConfiguration> configs) {
        log.info("[REST] Salvando em lote {} configurações de médicos.", configs != null ? configs.size() : 0);
        if (configs == null || configs.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<DoctorConfiguration> savedList = configs.stream()
                .map(doctorConfigurationRepository::save)
                .toList();
        return ResponseEntity.ok(savedList);
    }

    /**
     * Atualiza a URL do Google Review de um médico específico pelo ID.
     *
     * @param id ID do profissional Feegow.
     * @param payload Mapa contendo a nova googleReviewUrl.
     * @return Configuração atualizada do médico ou 404 Not Found.
     */
    @org.springframework.web.bind.annotation.PatchMapping("/{id}/google-review-url")
    public ResponseEntity<DoctorConfiguration> updateGoogleReviewUrl(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload) {
        String googleReviewUrl = payload.get("googleReviewUrl");
        log.info("[REST] Atualizando googleReviewUrl para o profissional ID: {} (url='{}')", id, googleReviewUrl);
        return doctorConfigurationRepository.findById(id)
                .map(config -> {
                    config.setGoogleReviewUrl(googleReviewUrl);
                    DoctorConfiguration updated = doctorConfigurationRepository.save(config);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Atualiza em lote a URL do Google Review para múltiplos médicos.
     *
     * @param googleReviewUrlsMap Mapa com a chave = ID do profissional e valor = nova googleReviewUrl.
     * @return Resposta 200 OK com a quantidade de médicos atualizados.
     */
    @org.springframework.web.bind.annotation.PatchMapping("/google-review-url/batch")
    public ResponseEntity<java.util.Map<String, Object>> updateGoogleReviewUrlBatch(
            @RequestBody java.util.Map<Long, String> googleReviewUrlsMap) {
        log.info("[REST] Atualizando googleReviewUrl em lote para {} médicos.", googleReviewUrlsMap != null ? googleReviewUrlsMap.size() : 0);
        if (googleReviewUrlsMap == null || googleReviewUrlsMap.isEmpty()) {
            return ResponseEntity.ok(java.util.Map.of("updatedCount", 0));
        }

        int updatedCount = 0;
        for (var entry : googleReviewUrlsMap.entrySet()) {
            Long doctorId = entry.getKey();
            String url = entry.getValue();
            var configOpt = doctorConfigurationRepository.findById(doctorId);
            if (configOpt.isPresent()) {
                var config = configOpt.get();
                config.setGoogleReviewUrl(url);
                doctorConfigurationRepository.save(config);
                updatedCount++;
            }
        }

        return ResponseEntity.ok(java.util.Map.of("updatedCount", updatedCount));
    }

    /**
     * Remove a configuração de um médico pelo ID.
     *
     * @param id ID do profissional Feegow.
     * @return Resposta 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        log.info("[REST] Removendo configuração do profissional ID: {}", id);
        doctorConfigurationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
