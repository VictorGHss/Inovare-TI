package br.dev.ctrls.inovareti.domain.user.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.user.Sector;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.dto.UpdateUserRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Use case: updates an existing user's name, email, role and sector.
 * Email uniqueness is validated excluding the user being edited.
 */
@Component
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;

    @Transactional
    public UserResponseDTO execute(UUID userId, UpdateUserRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        // Check email conflict only against other users
        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }

        Sector sector = sectorRepository.findById(request.sectorId())
                .orElseThrow(() -> new NotFoundException("Sector not found: " + request.sectorId()));

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setSector(sector);

        return UserResponseDTO.from(userRepository.save(user));
    }
}
