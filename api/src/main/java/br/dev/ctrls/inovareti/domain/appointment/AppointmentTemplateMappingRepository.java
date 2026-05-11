package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentTemplateMappingRepository extends JpaRepository<AppointmentTemplateMapping, UUID> {

    @Query("SELECT t FROM AppointmentTemplateMapping t WHERE TRIM(UPPER(t.templateName)) = TRIM(UPPER(:name)) ORDER BY t.placeholderIndex ASC")
    List<AppointmentTemplateMapping> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(@Param("name") String name);

    List<AppointmentTemplateMapping> findByTemplateNameOrderByPlaceholderIndexAsc(String templateName);

    void deleteAllByTemplateName(String templateName);
}