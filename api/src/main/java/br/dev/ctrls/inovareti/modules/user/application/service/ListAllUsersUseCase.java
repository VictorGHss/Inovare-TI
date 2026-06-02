package br.dev.ctrls.inovareti.modules.user.application.service;

import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: lista todos os usuários cadastrados.
 * O setor de cada usuário é carregado via JOIN para evitar problema N+1.
 */
@Component
@RequiredArgsConstructor
public class ListAllUsersUseCase {

    private final UserRepositoryPort userRepository;

    /**
     * Retorna todos os usuários do sistema.
     *
     * @return lista de DTOs com os dados públicos dos usuários
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> execute() {
        return userRepository.findAllWithSector()
                .stream()
                .map(UserResponseDTO::from)
                .toList();
    }
}
