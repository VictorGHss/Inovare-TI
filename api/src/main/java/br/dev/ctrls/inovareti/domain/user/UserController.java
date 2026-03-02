package br.dev.ctrls.inovareti.domain.user;

import br.dev.ctrls.inovareti.domain.user.dto.UserRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import br.dev.ctrls.inovareti.domain.user.usecase.CreateUserUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.ListAllUsersUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST para gerenciamento de usuários.
 * Base path: /api/users
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ListAllUsersUseCase listAllUsersUseCase;

    /**
     * Cria um novo usuário.
     * Retorna 201 Created com o usuário criado no corpo da resposta.
     */
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createUserUseCase.execute(request));
    }

    /**
     * Lista todos os usuários cadastrados.
     * Retorna 200 OK com a lista.
     */
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllUsersUseCase.execute());
    }
}
