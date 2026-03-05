package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByUserId(UUID userId);
    
    Optional<Asset> findByPatrimonyCode(String patrimonyCode);
    
    boolean existsByPatrimonyCode(String patrimonyCode);
}
