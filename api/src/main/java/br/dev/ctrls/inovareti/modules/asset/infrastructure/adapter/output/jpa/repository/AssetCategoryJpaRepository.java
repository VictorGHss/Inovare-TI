package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output.jpa.repository;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCategoryJpaRepository extends JpaRepository<AssetCategory, UUID> {

    Optional<AssetCategory> findByName(String name);

    boolean existsByName(String name);
}
