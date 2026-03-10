package br.dev.ctrls.inovareti.domain.settings;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    List<SystemSetting> findAllByOrderByIdAsc();
}
