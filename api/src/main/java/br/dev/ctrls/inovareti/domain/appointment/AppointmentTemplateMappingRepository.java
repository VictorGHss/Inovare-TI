package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentTemplateMappingRepository extends JpaRepository<AppointmentTemplateMapping, UUID> {

    List<AppointmentTemplateMapping> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(String templateName);

    @Modifying
    @Query("DELETE FROM AppointmentTemplateMapping m WHERE m.templateName = :templateName")
    void deleteByTemplateName(@Param("templateName") String templateName);
}