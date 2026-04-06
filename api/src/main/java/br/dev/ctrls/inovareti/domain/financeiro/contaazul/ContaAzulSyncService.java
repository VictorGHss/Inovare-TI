package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMapping;
import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMappingRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContaAzulSyncService {

    private final ContaAzulClient contaAzulClient;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;
    private final UserRepository userRepository;

    public SyncDoctorsResult syncAllDoctorsFromContaAzul() {
        List<ContaAzulClient.PessoaItem> pessoas = contaAzulClient.fetchAllPessoas();

        int created = 0;
        int updated = 0;

        for (ContaAzulClient.PessoaItem pessoa : pessoas) {
            if (!StringUtils.hasText(pessoa.id()) || !StringUtils.hasText(pessoa.email())) {
                continue;
            }

            String customerUuid = pessoa.id().trim();
            String doctorEmail = pessoa.email().trim();
            String doctorName = StringUtils.hasText(pessoa.nome()) ? pessoa.nome().trim() : null;

            User matchedUser = userRepository.findByEmail(doctorEmail).orElse(null);

            DoctorEmailMapping mapping = doctorEmailMappingRepository
                    .findByContaAzulCustomerUuid(customerUuid)
                    .orElse(null);

            if (mapping == null) {
                DoctorEmailMapping newMapping = DoctorEmailMapping.builder()
                        .contaAzulCustomerUuid(customerUuid)
                        .doctorName(doctorName)
                        .doctorEmail(doctorEmail)
                        .user(matchedUser)
                        .build();

                doctorEmailMappingRepository.save(newMapping);
                created++;
                continue;
            }

            mapping.setDoctorName(doctorName);
            mapping.setDoctorEmail(doctorEmail);

            if (matchedUser != null) {
                mapping.setUser(matchedUser);
            }

            doctorEmailMappingRepository.save(mapping);
            updated++;
        }

        return new SyncDoctorsResult(created, updated);
    }
}