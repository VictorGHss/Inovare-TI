package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.modules.finance.domain.port.DoctorEmailMappingRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.DoctorEmailMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
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
    private final UserRepositoryPort userRepository;

    /**
     * Lista os mapeamentos de e-mails de médicos para recibos.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @GetMapping
    public ResponseEntity<List<DoctorMappingResponseDTO>> listMappings() {
        List<DoctorMappingResponseDTO> response = doctorEmailMappingRepository.findAllByOrderByDoctorNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Cria um novo mapeamento de e-mail de médico para a Conta Azul.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @PostMapping
    public ResponseEntity<DoctorMappingResponseDTO> createMapping(@RequestBody @Valid UpsertDoctorMappingRequest request) {
        String customerUuid = request.contaAzulCustomerUuid().trim();
        String doctorName = normalizeNullable(request.doctorName());
        String doctorEmail = normalizeNullable(request.doctorEmail());
        String doctorCpfCnpj = normalizeNullable(request.doctorCpfCnpj());

        if (doctorEmailMappingRepository.findByContaAzulCustomerUuid(customerUuid).isPresent()) {
            throw new BadRequestException("Já existe mapeamento para o UUID informado.");
        }

        User linkedUser = resolveLinkedUser(request.userId());
        if (linkedUser == null && !StringUtils.hasText(doctorEmail)) {
            throw new BadRequestException("Informe um usuário vinculado ou um e-mail de fallback.");
        }

        DoctorEmailMapping saved = doctorEmailMappingRepository.save(DoctorEmailMapping.builder()
                .user(linkedUser)
                .doctorName(doctorName)
                .contaAzulCustomerUuid(customerUuid)
                .doctorEmail(doctorEmail)
                .doctorCpfCnpj(doctorCpfCnpj)
                .build());

        log.info("Mapeamento de médico criado com sucesso. customerUuid={}, userId={}, emailFallback={}",
                customerUuid,
                linkedUser != null ? linkedUser.getId() : null,
                doctorEmail);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * Atualiza um mapeamento existente de e-mail de médico.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<DoctorMappingResponseDTO> updateMapping(
            @PathVariable UUID id,
            @RequestBody @Valid UpsertDoctorMappingRequest request) {
        DoctorEmailMapping mapping = doctorEmailMappingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mapeamento de médico não encontrado."));

        String customerUuid = request.contaAzulCustomerUuid().trim();
        String doctorName = normalizeNullable(request.doctorName());
        String doctorEmail = normalizeNullable(request.doctorEmail());

        doctorEmailMappingRepository.findByContaAzulCustomerUuid(customerUuid)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Já existe mapeamento para o UUID informado.");
                });

        User linkedUser = resolveLinkedUser(request.userId());
        if (linkedUser == null && !StringUtils.hasText(doctorEmail)) {
            throw new BadRequestException("Informe um usuário vinculado ou um e-mail de fallback.");
        }

        mapping.setUser(linkedUser);
        mapping.setDoctorName(doctorName);
        mapping.setContaAzulCustomerUuid(customerUuid);
        mapping.setDoctorEmail(doctorEmail);

        if (request.doctorCpfCnpj() != null) {
            mapping.setDoctorCpfCnpj(normalizeNullable(request.doctorCpfCnpj()));
        }

        DoctorEmailMapping saved = doctorEmailMappingRepository.save(mapping);
        log.info("Mapeamento de médico atualizado com sucesso. id={}, customerUuid={}, userId={}, emailFallback={}",
                id,
                customerUuid,
                linkedUser != null ? linkedUser.getId() : null,
                doctorEmail);

        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * Remove um mapeamento existente de e-mail de médico.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        DoctorEmailMapping mapping = doctorEmailMappingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mapeamento de médico não encontrado."));

        doctorEmailMappingRepository.delete(mapping);
        log.info("Mapeamento de médico removido com sucesso. id={}, customerUuid={}", id, mapping.getContaAzulCustomerUuid());

        return ResponseEntity.noContent().build();
    }

    private DoctorMappingResponseDTO toResponse(DoctorEmailMapping mapping) {
        String resolvedDoctorName = StringUtils.hasText(mapping.getDoctorName())
                ? mapping.getDoctorName()
                : mapping.getUser() != null ? mapping.getUser().getName() : null;

        String resolvedDoctorEmail = StringUtils.hasText(mapping.getDoctorEmail())
                ? mapping.getDoctorEmail()
                : mapping.getUser() != null ? mapping.getUser().getEmail() : null;

        return new DoctorMappingResponseDTO(
                mapping.getId(),
                mapping.getUser() != null ? mapping.getUser().getId() : null,
                mapping.getUser() != null ? mapping.getUser().getContaAzulId() : null,
                resolvedDoctorName,
                mapping.getContaAzulCustomerUuid(),
                resolvedDoctorEmail,
                mapping.getDoctorCpfCnpj(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt());
    }

    private User resolveLinkedUser(UUID userId) {
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado para o userId informado."));
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    public record UpsertDoctorMappingRequest(
            UUID userId,
            String doctorName,
            @NotBlank(message = "O UUID do cliente da Conta Azul é obrigatório.")
            String contaAzulCustomerUuid,
            @Email(message = "Informe um e-mail válido para o médico.")
            String doctorEmail,
            String doctorCpfCnpj) {
    }

    public record DoctorMappingResponseDTO(
            UUID id,
            UUID userId,
            String userContaAzulId,
            String doctorName,
            String contaAzulCustomerUuid,
            String doctorEmail,
            String doctorCpfCnpj,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
