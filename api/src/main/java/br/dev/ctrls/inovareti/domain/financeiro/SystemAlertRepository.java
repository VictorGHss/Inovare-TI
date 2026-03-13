package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAlertRepository extends JpaRepository<SystemAlert, UUID> {

	List<SystemAlert> findAllByOrderByCreatedAtDesc();
}
