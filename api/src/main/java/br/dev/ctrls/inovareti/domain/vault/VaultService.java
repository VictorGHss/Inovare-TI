package br.dev.ctrls.inovareti.domain.vault;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultCreateItemRequestDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultItemResponseDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultSecretResponseDTO;
import br.dev.ctrls.inovareti.infra.security.EncryptionService;
import br.dev.ctrls.inovareti.infra.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VaultService {

    private static final Set<String> ALLOWED_FILE_CONTENT_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "image/webp",
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final VaultItemRepository vaultItemRepository;
    private final VaultItemShareRepository vaultItemShareRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final LocalFileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Transactional
    public VaultItemResponseDTO createItem(UUID authenticatedUserId, VaultCreateItemRequestDTO request, MultipartFile file, String ipAddress) {
        User owner = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

        validateCreateRequest(request);

        String secretContent = request.secretContent();
        if (request.itemType() == VaultItemType.CREDENTIAL && secretContent != null && !secretContent.isBlank()) {
            secretContent = encryptionService.encrypt(secretContent);
        }

        String storedFilePath = storeVaultFileIfPresent(file);

        LocalDateTime now = LocalDateTime.now();
        VaultItem item = VaultItem.builder()
                .title(request.title())
                .description(request.description())
                .itemType(request.itemType())
                .secretContent(secretContent)
                .filePath(storedFilePath)
                .owner(owner)
                .sharingType(request.sharingType())
                .createdAt(now)
                .updatedAt(now)
                .build();

        VaultItem savedItem = vaultItemRepository.save(item);
        createCustomShares(savedItem, request.sharedWithUserIds());

        // Registra criação de item no cofre na trilha de auditoria
        auditLogService.publish(AuditEvent.of(AuditAction.VAULT_ITEM_CREATE)
                .userId(authenticatedUserId)
                .resourceType("VaultItem")
                .resourceId(savedItem.getId())
                .details("{\"itemTitle\": \"" + savedItem.getTitle() + "\"}")
                .ipAddress(ipAddress)
                .build());

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
    public VaultSecretResponseDTO getSecret(UUID authenticatedUserId, UUID itemId, String ipAddress) {
        VaultItem item = findAccessibleItem(authenticatedUserId, itemId);

        if (item.getSecretContent() == null || item.getSecretContent().isBlank()) {
            throw new BadRequestException("Este item não possui conteúdo secreto.");
        }

        String content = item.getSecretContent();
        if (item.getItemType() == VaultItemType.CREDENTIAL) {
            content = encryptionService.decrypt(content);
        }

        // Evento crítico: leitura de segredo do Vault
        auditLogService.publish(AuditEvent.of(AuditAction.VAULT_SECRET_VIEW)
                .userId(authenticatedUserId)
                .resourceType("VaultItem")
                .resourceId(item.getId())
                .details("{\"itemTitle\": \"" + item.getTitle() + "\"}")
                .ipAddress(ipAddress)
                .build());

        return new VaultSecretResponseDTO(item.getId(), content);
    }

    @Transactional(readOnly = true)
    public VaultItem findAccessibleItem(UUID authenticatedUserId, UUID itemId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

        VaultItem item = vaultItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item do cofre não encontrado."));

        if (!canUserAccessItem(user, item)) {
            throw new AccessDeniedException("Você não possui permissão para acessar este item do cofre.");
        }

        return item;
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

    private String storeVaultFileIfPresent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FILE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Tipo de arquivo não permitido para o cofre.");
        }

        try {
            return fileStorageService.store(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao armazenar o anexo do cofre.", ex);
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