package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/financeiro/doctor-mappings")
@RequiredArgsConstructor
public class DoctorMappingController {

    private final DoctorEmailMappingRepository doctorEmailMappingRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<DoctorMappingResponseDTO>> listMappings() {
        List<DoctorMappingResponseDTO> response = doctorEmailMappingRepository.findAllByOrderByDoctorNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DoctorMappingResponseDTO> createMapping(@RequestBody @Valid UpsertDoctorMappingRequest request) {
        String doctorName = request.doctorName().trim();
        String customerUuid = request.contaAzulCustomerUuid().trim();
        String doctorEmail = request.doctorEmail().trim();

        if (doctorEmailMappingRepository.findByContaAzulCustomerUuid(customerUuid).isPresent()) {
            throw new BadRequestException("Já existe mapeamento para o UUID informado.");
        }

        DoctorEmailMapping saved = doctorEmailMappingRepository.save(DoctorEmailMapping.builder()
            .doctorName(doctorName)
                .contaAzulCustomerUuid(customerUuid)
                .doctorEmail(doctorEmail)
                .build());

        log.info("Mapeamento de médico criado com sucesso. customerUuid={}, email={}", customerUuid, doctorEmail);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        DoctorEmailMapping mapping = doctorEmailMappingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mapeamento de médico não encontrado."));

        doctorEmailMappingRepository.delete(mapping);
        log.info("Mapeamento de médico removido com sucesso. id={}, customerUuid={}", id, mapping.getContaAzulCustomerUuid());

        return ResponseEntity.noContent().build();
    }

    private DoctorMappingResponseDTO toResponse(DoctorEmailMapping mapping) {
        return new DoctorMappingResponseDTO(
                mapping.getId(),
            mapping.getDoctorName(),
                mapping.getContaAzulCustomerUuid(),
                mapping.getDoctorEmail(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt());
    }

    public record UpsertDoctorMappingRequest(
            @NotBlank(message = "O nome do médico é obrigatório.")
            String doctorName,
            @NotBlank(message = "O UUID do cliente da Conta Azul é obrigatório.")
            String contaAzulCustomerUuid,
            @NotBlank(message = "O e-mail do médico é obrigatório.")
            @Email(message = "Informe um e-mail válido para o médico.")
            String doctorEmail) {
    }

    public record DoctorMappingResponseDTO(
            UUID id,
            String doctorName,
            String contaAzulCustomerUuid,
            String doctorEmail,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}