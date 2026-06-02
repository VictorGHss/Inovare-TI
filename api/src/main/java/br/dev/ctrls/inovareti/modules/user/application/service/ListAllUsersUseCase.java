package br.dev.ctrls.inovareti.modules.user.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: lista todos os usuÃ¡rios cadastrados.
 * O setor de cada usuÃ¡rio Ã© carregado via JOIN para evitar problema N+1.
 */
@Component
@RequiredArgsConstructor
@Observed
public class ListAllUsersUseCase {

    private final UserRepositoryPort userRepository;

    /**
     * Retorna todos os usuÃ¡rios do sistema.
     *
     * @return lista de DTOs com os dados pÃºblicos dos usuÃ¡rios
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> execute() {
        return userRepository.findAllWithSector()
                .stream()
                .map(UserResponseDTO::from)
                .toList();
    }
}


