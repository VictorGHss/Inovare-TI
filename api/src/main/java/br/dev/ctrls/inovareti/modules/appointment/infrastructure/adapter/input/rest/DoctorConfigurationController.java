package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@RequestMapping("/api/v1/doctors/configurations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DoctorConfigurationController {

    private final DoctorConfigurationRepository doctorConfigurationRepository;

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
     * Remove a configuração de um médico pelo ID.
     *
     * @param id ID do profissional Feegow.
     * @return Resposta 244 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        log.info("[REST] Removendo configuração do profissional ID: {}", id);
        doctorConfigurationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
