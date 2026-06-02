package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

/**
 * Componente responsÃ¡vel pela higienizaÃ§Ã£o, limpeza de strings e tratamento
 * de dados brutos de profissionais (mÃ©dicos) vindos da API do Feegow ERP.
 */
@Component
@Observed
public class BlipTextSanitizer {

    /**
     * Sanitiza o nome do profissional (mÃ©dico), separando-o rigorosamente
     * de nomes de procedimentos, filas ou pacientes de teste concatenados.
     *
     * @param doctorName nome bruto vindo da Feegow ou do mapeamento
     * @return nome do profissional higienizado
     */
    public String sanitizeDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return "ClÃ­nica Inovare";
        }
        String clean = doctorName.trim();
        // Separar de hifens (ex: "Dr. JoÃ£o - Cardiologia")
        if (clean.contains(" - ")) {
            clean = clean.split(" - ")[0].trim();
        } else if (clean.contains("-")) {
            clean = clean.split("-")[0].trim();
        }
        // Separar de parÃªnteses (ex: "Dr. JoÃ£o (Ortopedia)")
        if (clean.contains("(")) {
            clean = clean.split("\\(")[0].trim();
        }
        // Separar de barras (ex: "Dr. JoÃ£o/Cardiologia")
        if (clean.contains("/")) {
            clean = clean.split("/")[0].trim();
        }
        // Remove prefixos como Dr., Dra., etc.
        clean = clean.replaceAll("(?i)^(Dr\\.|Dra\\.|Dr|Dra)\\s+", "");
        return clean.trim();
    }

    /**
     * Remove prefixos simples do nome do mÃ©dico (Dr., Dra., etc.).
     *
     * @param doctorName nome bruto do mÃ©dico
     * @return nome sem prefixos
     */
    public String cleanDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return "ClÃ­nica Inovare";
        }
        String clean = doctorName.trim();
        clean = clean.replaceAll("(?i)^(Dr\\.|Dra\\.|Dr|Dra)\\s+", "");
        return clean.trim();
    }
}


