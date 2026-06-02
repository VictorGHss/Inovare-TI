package br.dev.ctrls.inovareti.modules.asset.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;

/**
 * DTO de resposta de Ativo com suporte multiusuário.
 *
 * <p><b>Retrocompatibilidade:</b> os campos legados {@code userId} e {@code assignedToName}
 * são mantidos e preenchidos defensivamente com o primeiro elemento da lista de usuários,
 * garantindo que o painel React e outros consumidores da API não quebrem.
 *
 * <p><b>Novos campos multiusuário:</b> {@code userIds} e {@code assignedToNames} expõem
 * a lista completa de usuários vinculados ao ativo.
 */
public record AssetResponseDTO(
        UUID id,

        // ── Campos legados (retrocompatibilidade 1:N) ──────────────────────
        UUID userId,
        String assignedToName,

        // ── Campos multiusuário (nova realidade N:N) ───────────────────────
        List<UUID> userIds,
        List<String> assignedToNames,

        // ── Dados do ativo ─────────────────────────────────────────────────
        String name,
        String patrimonyCode,
        UUID categoryId,
        String categoryName,
        String specifications,
        LocalDateTime createdAt,
        String invoiceFileName
) {
    /**
     * Constrói o DTO a partir da entidade {@link Asset}.
     *
     * <p>Os campos legados {@code userId} e {@code assignedToName} são preenchidos
     * com o primeiro usuário encontrado na coleção (ordem não garantida),
     * ou {@code null} caso o ativo não tenha nenhum usuário vinculado.
     *
     * @param asset entidade de ativo com a coleção {@code users} já inicializada
     */
    public static AssetResponseDTO from(Asset asset) {
        List<User> userList = asset.getUsers() != null
                ? asset.getUsers().stream().toList()
                : List.of();

        UUID primeiroUserId = userList.isEmpty() ? null : userList.getFirst().getId();
        String primeiroNome  = userList.isEmpty() ? null : userList.getFirst().getName();

        List<UUID> todosIds   = userList.stream().map(User::getId).toList();
        List<String> todosNomes = userList.stream().map(User::getName).toList();

        return new AssetResponseDTO(
                asset.getId(),
                primeiroUserId,
                primeiroNome,
                todosIds,
                todosNomes,
                asset.getName(),
                asset.getPatrimonyCode(),
                asset.getCategory() != null ? asset.getCategory().getId() : null,
                asset.getCategory() != null ? asset.getCategory().getName() : null,
                asset.getSpecifications(),
                asset.getCreatedAt(),
                asset.getInvoiceFileName()
        );
    }

    /**
     * Factory legado mantido para não quebrar chamadas existentes durante a migração.
     * Delega para {@link #from(Asset)}, ignorando o parâmetro {@code assignedToUser}.
     *
     * @deprecated Prefira {@link #from(Asset)} — o usuário agora é derivado da coleção interna do ativo.
     */
    @Deprecated(since = "Round-4", forRemoval = true)
    public static AssetResponseDTO from(Asset asset, User assignedToUser) {
        return from(asset);
    }
}
