package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import lombok.Getter;
import java.util.Collections;
import java.util.Set;

/**
 * Catálogo estático dos médicos, especialidades e exames da Clínica Inovare.
 * Utilizado para pontuação, busca fatiada por tokens e roteamento de intenções.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Getter
public enum DoctorCatalog {

    DR_KOGA(
        "Dr. Carlos Heidi Koga",
        "Urologia",
        "DESK",
        "Urologia - Dr. Carlos Heidi Koga",
        Set.of("carlos", "koga", "heidi", "uro", "urologia")
    ),
    DRA_CAROLINE_SAAD(
        "Dra. Caroline Tatim Saad",
        "Gastroenterologia",
        "EXTERNAL_WA",
        "Gastroenterologia - Dra. Caroline Tatim Saad",
        Set.of("caroline", "tatim", "saad", "gastro", "gastroenterologia")
    ),
    DR_CLAUDIO_SOLAK(
        "Dr. Claudio Solak",
        "Gastroenterologia",
        "EXTERNAL_WA",
        "Gastroenterologia - Dr. Claudio Solak",
        Set.of("claudio", "solak", "gastro", "gastroenterologia")
    ),
    DR_DANILO_SAAD(
        "Dr. Danilo Saad",
        "Gastroenterologia",
        "EXTERNAL_WA",
        "Gastroenterologia - Dr. Danilo Saad",
        Set.of("danilo", "saad", "gastro", "gastroenterologia")
    ),
    DR_ALEXANDRE_ACUNA(
        "Dr. Alexandre Barão Acuña",
        "Endocrinologia",
        "EXTERNAL_WA",
        "Endocrinologia - Dr. Alexandre Barão Acuña",
        Set.of("alexandre", "barao", "acuna", "endocrino", "endocrinologia")
    ),
    DR_MARCELO_TESSARI(
        "Dr. Marcelo Tessari",
        "Neurologia",
        "DESK",
        "Neurologia - Dr. Marcelo Tessari",
        Set.of("marcelo", "tessari", "neuro", "neurologia")
    ),
    DRA_VANIA_GULIN(
        "Dra. Vânia Gulin",
        "Alergia e Imunologia",
        "DESK",
        "Alergia e Imunologia - Dra. Vânia Gulin",
        Set.of("vania", "gulin", "alergia", "imunologia", "imuno")
    ),
    DR_JOELSON_GULIN(
        "Dr. Joelson José Gulin",
        "Cirurgia Ap. Digestivo",
        "DESK",
        "Cirurgia Ap. Digestivo - Dr. Joelson José Gulin",
        Set.of("joelson", "gulin", "digestivo", "cirurgia")
    ),
    DR_MARCELO_FERREIRA(
        "Dr. Marcelo Valladao Ferreira",
        "Cardiologia",
        "DESK",
        "Cardiologia - Dr. Marcelo Valladao Ferreira",
        Set.of("marcelo", "valladao", "ferreira", "cardio", "cardiologia")
    ),
    DR_RUBENS_SIRTOLI(
        "Dr. Rubens Sirtoli Filho",
        "Cardiologia",
        "DESK",
        "Cardiologia - Dr. Rubens Sirtoli Filho",
        Set.of("rubens", "sirtoli", "cardio", "cardiologia")
    ),
    DR_GIULIANO_DORETTO(
        "Dr. Giuliano Schultz Doretto",
        "Dermatologia",
        "DESK",
        "Dermatologia - Dr. Giuliano Schultz Doretto",
        Set.of("giuliano", "doretto", "schultz", "dermato", "dermatologia")
    ),
    EXAMES_DIGESTIVOS(
        "Endoscopia / Colonoscopia",
        "Exames Digestivos",
        "DESK",
        "Exames Digestivos - Endoscopia / Colonoscopia",
        Set.of("endoscopia", "colonoscopia", "phmetria")
    ),
    EXAMES_IMAGEM(
        "Clínica da Imagem / Clinipon",
        "Exames de Imagem",
        "EXTERNAL_WA",
        "Exames de Imagem - Clínica da Imagem / Clinipon",
        Set.of("imagem", "clinipon", "ecocardiograma", "holter", "mapa", "ultrassom")
    );

    private final String doctorName;
    private final String specialty;
    private final String route;
    private final String queue;
    private final Set<String> tokens;

    DoctorCatalog(String doctorName, String specialty, String route, String queue, Set<String> tokens) {
        this.doctorName = doctorName;
        this.specialty = specialty;
        this.route = route;
        this.queue = queue;
        this.tokens = Collections.unmodifiableSet(tokens);
    }
}
