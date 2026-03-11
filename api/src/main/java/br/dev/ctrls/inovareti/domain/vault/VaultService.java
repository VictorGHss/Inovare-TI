package br.dev.ctrls.inovareti.domain.vault;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultCreateItemRequestDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultItemResponseDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultSecretResponseDTO;
import br.dev.ctrls.inovareti.infra.security.EncryptionService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VaultService {

    private final VaultItemRepository vaultItemRepository;
    private final VaultItemShareRepository vaultItemShareRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public VaultItemResponseDTO createItem(UUID authenticatedUserId, VaultCreateItemRequestDTO request) {
        User owner = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

        validateCreateRequest(request);

        String secretContent = request.secretContent();
        if (request.itemType() == VaultItemType.CREDENTIAL && secretContent != null && !secretContent.isBlank()) {
            secretContent = encryptionService.encrypt(secretContent);
        }

        LocalDateTime now = LocalDateTime.now();
        VaultItem item = VaultItem.builder()
                .title(request.title())
                .description(request.description())
                .itemType(request.itemType())
                .secretContent(secretContent)
                .filePath(request.filePath())
                .owner(owner)
                .sharingType(request.sharingType())
                .createdAt(now)
                .updatedAt(now)
                .build();

        VaultItem savedItem = vaultItemRepository.save(item);
        createCustomShares(savedItem, request.sharedWithUserIds());

        return VaultItemResponseDTO.from(savedItem);
    }

    @Transactional(readOnly = true)
    public List<VaultItemResponseDTO> listVisibleItems(UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

        boolean isTechAdmin = user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.TECHNICIAN;
        return vaultItemRepository.findVisibleItems(authenticatedUserId, isTechAdmin)
                .stream()
                .map(VaultItemResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VaultSecretResponseDTO getSecret(UUID authenticatedUserId, UUID itemId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

        VaultItem item = vaultItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item do cofre não encontrado."));

        if (!canUserAccessItem(user, item)) {
            throw new AccessDeniedException("Você não possui permissão para acessar este item do cofre.");
        }

        if (item.getSecretContent() == null || item.getSecretContent().isBlank()) {
            throw new BadRequestException("Este item não possui conteúdo secreto.");
        }

        String content = item.getSecretContent();
        if (item.getItemType() == VaultItemType.CREDENTIAL) {
            content = encryptionService.decrypt(content);
        }

        return new VaultSecretResponseDTO(item.getId(), content);
    }

    private void validateCreateRequest(VaultCreateItemRequestDTO request) {
        if (request.itemType() == VaultItemType.CREDENTIAL
                && (request.secretContent() == null || request.secretContent().isBlank())) {
            throw new BadRequestException("O conteúdo secreto é obrigatório para itens do tipo CREDENTIAL.");
        }

        if (request.sharingType() == VaultSharingType.CUSTOM
                && (request.sharedWithUserIds() == null || request.sharedWithUserIds().isEmpty())) {
            throw new BadRequestException("É necessário informar ao menos um usuário para compartilhamento CUSTOM.");
        }
    }

    private void createCustomShares(VaultItem item, List<UUID> sharedWithUserIds) {
        if (item.getSharingType() != VaultSharingType.CUSTOM || sharedWithUserIds == null || sharedWithUserIds.isEmpty()) {
            return;
        }

        List<User> users = userRepository.findAllById(sharedWithUserIds);
        if (users.size() != sharedWithUserIds.size()) {
            throw new NotFoundException("Um ou mais usuários informados para compartilhamento não foram encontrados.");
        }

        List<VaultItemShare> shares = users.stream()
                .map(sharedUser -> VaultItemShare.builder()
                        .vaultItem(item)
                        .sharedWithUser(sharedUser)
                        .build())
                .toList();
        vaultItemShareRepository.saveAll(shares);
    }

    private boolean canUserAccessItem(User user, VaultItem item) {
        if (item.getOwner().getId().equals(user.getId())) {
            return true;
        }

        if (item.getSharingType() == VaultSharingType.ALL_TECH_ADMIN) {
            return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.TECHNICIAN;
        }

        if (item.getSharingType() == VaultSharingType.CUSTOM) {
            return vaultItemShareRepository.existsByVaultItemIdAndSharedWithUserId(item.getId(), user.getId());
        }

        return false;
    }
}