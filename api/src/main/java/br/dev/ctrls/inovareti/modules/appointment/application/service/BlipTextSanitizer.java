package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

/**
 * Componente responsável pela higienização, limpeza de strings e tratamento
 * de dados brutos de profissionais (médicos) vindos da API do Feegow ERP.
 */
@Component
@Observed
public class BlipTextSanitizer {

    /**
     * Sanitiza o nome do profissional (médico), separando-o rigorosamente
     * de nomes de procedimentos, filas ou pacientes de teste concatenados.
     *
     * @param doctorName nome bruto vindo da Feegow ou do mapeamento
     * @return nome do profissional higienizado
     */
    public String sanitizeDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return "Clínica Inovare";
        }
        String clean = doctorName.trim();
        // Separar de hifens (ex: "Dr. João - Cardiologia")
        if (clean.contains(" - ")) {
            clean = clean.split(" - ")[0].trim();
        } else if (clean.contains("-")) {
            clean = clean.split("-")[0].trim();
        }
        // Separar de parênteses (ex: "Dr. João (Ortopedia)")
        if (clean.contains("(")) {
            clean = clean.split("\\(")[0].trim();
        }
        // Separar de barras (ex: "Dr. João/Cardiologia")
        if (clean.contains("/")) {
            clean = clean.split("/")[0].trim();
        }
        // Remove prefixos como Dr., Dra., etc.
        clean = clean.replaceAll("(?i)^(Dr\\.|Dra\\.|Dr|Dra)\\s+", "");
        return clean.trim();
    }

    /**
     * Remove prefixos simples do nome do médico (Dr., Dra., etc.).
     *
     * @param doctorName nome bruto do médico
     * @return nome sem prefixos
     */
    public String cleanDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return "Clínica Inovare";
        }
        String clean = doctorName.trim();
        clean = clean.replaceAll("(?i)^(Dr\\.|Dra\\.|Dr|Dra)\\s+", "");
        return clean.trim();
    }
}


