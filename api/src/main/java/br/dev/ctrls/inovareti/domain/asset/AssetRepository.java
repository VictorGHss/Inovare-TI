package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdIsNotNull();
    
    long countByUserIdIsNull();
    Optional<Asset> findByPatrimonyCode(String patrimonyCode);
    
    boolean existsByPatrimonyCode(String patrimonyCode);

    @Query("""
            select a
            from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
                and (
                    :status = 'ALL'
                    or (:status = 'IN_USE' and a.userId is not null)
                    or (:status = 'IN_STOCK' and a.userId is null)
                )
            order by a.createdAt desc
            """)
    List<Asset> findWithFiltersOrderByCreatedAtDesc(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status
    );

    @Query("""
            select a
            from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
                and (
                    :status = 'ALL'
                    or (:status = 'IN_USE' and a.userId is not null)
                    or (:status = 'IN_STOCK' and a.userId is null)
                )
            order by (
                select count(m.id)
                from AssetMaintenance m
                where m.asset = a
            ) desc, a.createdAt desc
            """)
    List<Asset> findWithFiltersOrderByMaintenanceCountDesc(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status
    );
}
