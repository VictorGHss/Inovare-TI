package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetJpaRepository extends JpaRepository<Asset, UUID> {

    /**
     * Busca todos os ativos vinculados a um usuário específico.
     * Spring Data deriva a query a partir da coleção {@code users}.
     */
    List<Asset> findByUsersId(UUID userId);

    /**
     * Conta ativos vinculados a um usuário específico.
     */
    long countByUsersId(UUID userId);

    /**
     * Conta ativos com pelo menos um usuário atribuído (status: EM USO).
     */
    @Query("select count(a) from Asset a where size(a.users) > 0")
    long countInUse();

    /**
     * Conta ativos sem nenhum usuário atribuído (status: NO ESTOQUE / disponível).
     */
    @Query("select count(a) from Asset a where size(a.users) = 0")
    long countInStock();

    Optional<Asset> findByPatrimonyCode(String patrimonyCode);

    boolean existsByPatrimonyCode(String patrimonyCode);

    @Query(value = """
            select a
            from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
                and (
                    :status = 'ALL'
                    or (:status = 'IN_USE' and size(a.users) > 0)
                    or (:status = 'IN_STOCK' and size(a.users) = 0)
                )
                and (:search is null or :search = '' or lower(a.name) like lower(concat('%', :search, '%')) or lower(a.patrimonyCode) like lower(concat('%', :search, '%')))
            order by a.createdAt desc
            """,
            countQuery = """
            select count(a)
            from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
                and (
                    :status = 'ALL'
                    or (:status = 'IN_USE' and size(a.users) > 0)
                    or (:status = 'IN_STOCK' and size(a.users) = 0)
                )
                and (:search is null or :search = '' or lower(a.name) like lower(concat('%', :search, '%')) or lower(a.patrimonyCode) like lower(concat('%', :search, '%')))
            """)
    org.springframework.data.domain.Page<Asset> findWithFiltersOrderByCreatedAtDesc(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    @Query(value = """
            select a
            from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
                and (
                    :status = 'ALL'
                    or (:status = 'IN_USE' and size(a.users) > 0)
                    or (:status = 'IN_STOCK' and size(a.users) = 0)
                )
                and (:search is null or :search = '' or lower(a.name) like lower(concat('%', :search, '%')) or lower(a.patrimonyCode) like lower(concat('%', :search, '%')))
            order by (
                select count(m.id)
                from AssetMaintenance m
                where m.asset = a
            ) desc, a.createdAt desc
            """,
            countQuery = """
            select count(a)
            from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
                and (
                    :status = 'ALL'
                    or (:status = 'IN_USE' and size(a.users) > 0)
                    or (:status = 'IN_STOCK' and size(a.users) = 0)
                )
                and (:search is null or :search = '' or lower(a.name) like lower(concat('%', :search, '%')) or lower(a.patrimonyCode) like lower(concat('%', :search, '%')))
            """)
    org.springframework.data.domain.Page<Asset> findWithFiltersOrderByMaintenanceCountDesc(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    // Conta assets vinculados a uma categoria (útil para validar deleção de categoria)
    long countByCategory_Id(UUID categoryId);
}
