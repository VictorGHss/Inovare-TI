package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import lombok.Getter;
import java.util.Collections;
import java.util.Set;

/**
 * Catálogo estático abrangente dos médicos, especialidades e exames da Clínica Inovare.
 * Utilizado para pontuação, busca fatiada por tokens e roteamento de intenções.
 */
@Getter
public enum DoctorCatalog {

    // 1. Alergia e Imunologia
    DRA_VANIA_GULIN(
        "Dra. Vânia Gulin",
        "Alergia e Imunologia",
        "DESK",
        "Alergia e Imunologia - Dra. Vânia Gulin",
        Set.of("vania", "gulin", "alergia", "imunologia", "alergologista", "imuno")
    ),

    // 2. Anestesiologia
    ANESTESIOLOGIA(
        "Anestesiologia (Secretaria do Setor)",
        "Anestesiologia",
        "DESK",
        "Anestesiologia - Secretaria",
        Set.of("anestesia", "anestesiologia", "anestesista")
    ),

    // 3. Cardiologia
    DRA_LILIANA_PILATTI(
        "Dra. Liliana Elias Pena Pilatti",
        "Cardiologia",
        "DESK",
        "Cardiologia - Dra. Liliana Elias Pena Pilatti",
        Set.of("liliana", "elias", "pena", "pilatti", "cardiologia", "cardio")
    ),
    DR_MARCELO_FERREIRA(
        "Dr. Marcelo Valladao Ferreira",
        "Cardiologia",
        "DESK",
        "Cardiologia - Dr. Marcelo Valladao Ferreira",
        Set.of("marcelo", "valladao", "ferreira", "cardiologia", "cardio")
    ),
    DR_RUBENS_SIRTOLI(
        "Dr. Rubens Sirtoli Filho",
        "Cardiologia",
        "DESK",
        "Cardiologia - Dr. Rubens Sirtoli Filho",
        Set.of("rubens", "sirtoli", "cardiologia", "cardio")
    ),

    // 4. Cirurgia do Aparelho Digestivo
    DR_CESAR_ODA(
        "Dr. Cesar Toshio Oda",
        "Cirurgia do Aparelho Digestivo",
        "DESK",
        "Cirurgia do Aparelho Digestivo - Dr. Cesar Toshio Oda",
        Set.of("cesar", "toshio", "oda", "digestivo", "cirurgia")
    ),
    DR_JOELSON_GULIN(
        "Dr. Joelson José Gulin",
        "Cirurgia do Aparelho Digestivo",
        "DESK",
        "Cirurgia do Aparelho Digestivo - Dr. Joelson José Gulin",
        Set.of("joelson", "jose", "gulin", "digestivo", "cirurgia")
    ),

    // 5. Cirurgia Geral
    DR_DANIEL_ODA(
        "Dr. Daniel Oda",
        "Cirurgia Geral",
        "DESK",
        "Cirurgia Geral - Dr. Daniel Oda",
        Set.of("daniel", "oda", "cirurgia", "geral")
    ),

    // 6. Cirurgia Plástica
    DR_VICTOR_MAURO(
        "Dr. Victor Mauro",
        "Cirurgia Plástica",
        "DESK",
        "Cirurgia Plástica - Dr. Victor Mauro",
        Set.of("victor", "mauro", "plastica", "cirurgia")
    ),

    // 7. Cirurgia Torácica
    DR_MAGNO_ZANELLATO(
        "Dr. Magno Zanellato",
        "Cirurgia Torácica",
        "DESK",
        "Cirurgia Torácica - Dr. Magno Zanellato",
        Set.of("magno", "zanellato", "toracica", "pneumologia", "pneumo")
    ),

    // 8. Cirurgia Vascular
    DR_BRUNO_PANCAN(
        "Dr. Bruno Figueiredo Pançan",
        "Cirurgia Vascular",
        "DESK",
        "Cirurgia Vascular - Dr. Bruno Figueiredo Pançan",
        Set.of("bruno", "figueiredo", "pancan", "vascular")
    ),
    DRA_KAREN_MIYABUKURO(
        "Dra. Karen Kono Miyabukuro",
        "Cirurgia Vascular",
        "DESK",
        "Cirurgia Vascular - Dra. Karen Kono Miyabukuro",
        Set.of("karen", "kono", "miyabukuro", "vascular")
    ),
    DR_RICARDO_GOMES(
        "Dr. Ricardo Zanetti Gomes",
        "Cirurgia Vascular",
        "DESK",
        "Cirurgia Vascular - Dr. Ricardo Zanetti Gomes",
        Set.of("ricardo", "zanetti", "gomes", "vascular")
    ),

    // 9. Clínica Geral
    DRA_ANA_PAULA_CARVALHO(
        "Dra. Ana Paula Costa Pádua de Carvalho",
        "Clínica Geral",
        "DESK",
        "Clínica Geral - Dra. Ana Paula Costa Pádua de Carvalho",
        Set.of("ana", "paula", "padua", "carvalho", "clinica", "geral", "clinico")
    ),
    DR_LUIZ_HENRIQUE_STRACK(
        "Dr. Luiz Henrique Strack",
        "Clínica Geral",
        "DESK",
        "Clínica Geral - Dr. Luiz Henrique Strack",
        Set.of("luiz", "henrique", "strack", "clinica", "geral", "clinico")
    ),

    // 10. Dermatologia
    DR_GIULIANO_DORETTO(
        "Dr. Giuliano Schultz Doretto Campanari",
        "Dermatologia",
        "DESK",
        "Dermatologia - Dr. Giuliano Schultz Doretto Campanari",
        Set.of("giuliano", "schultz", "doretto", "campanari", "dermatologia", "dermato")
    ),

    // 11. Endocrinologia
    DR_ALEXANDRE_ACUNA(
        "Dr. Alexandre Barão Acuña",
        "Endocrinologia",
        "EXTERNAL_WA",
        "Endocrinologia - Dr. Alexandre Barão Acuña",
        Set.of("alexandre", "barao", "acuna", "endocrinologia", "endocrino")
    ),

    // 12. Fisioterapia
    DRA_JULIANA_BORATO(
        "Dra. Juliana Borato",
        "Fisioterapia",
        "DESK",
        "Fisioterapia - Dra. Juliana Borato",
        Set.of("juliana", "borato", "fisioterapia", "fisio")
    ),

    // 13. Fonoaudiologia
    DRA_CINTIA_CENOVICZ(
        "Dra. Cíntia Simão Cenovicz",
        "Fonoaudiologia",
        "EXTERNAL_WA",
        "Fonoaudiologia - Dra. Cíntia Simão Cenovicz",
        Set.of("cintia", "simao", "cenovicz", "fonoaudiologia", "fono")
    ),

    // 14. Gastroenterologia
    DRA_CAROLINE_SAAD(
        "Dra. Caroline Tatim Saad",
        "Gastroenterologia",
        "EXTERNAL_WA",
        "Gastroenterologia - Dra. Caroline Tatim Saad",
        Set.of("caroline", "tatim", "saad", "gastroenterologia", "gastro")
    ),
    DR_CLAUDIO_SOLAK(
        "Dr. Claudio Solak",
        "Gastroenterologia",
        "EXTERNAL_WA",
        "Gastroenterologia - Dr. Claudio Solak",
        Set.of("claudio", "solak", "gastroenterologia", "gastro")
    ),
    DR_DANILO_SAAD(
        "Dr. Danilo Saad",
        "Gastroenterologia",
        "EXTERNAL_WA",
        "Gastroenterologia - Dr. Danilo Saad",
        Set.of("danilo", "saad", "gastroenterologia", "gastro")
    ),

    // 15. Ginecologia (WhatsApp Próprio)
    DRA_BRENDA_AGUIAR(
        "Dra. Brenda de Almeida Aguiar",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dra. Brenda de Almeida Aguiar",
        Set.of("brenda", "almeida", "aguiar", "ginecologia", "gineco")
    ),
    DR_CARLOS_BATISTA(
        "Dr. Carlos Alberto Batista da Silva",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dr. Carlos Alberto Batista da Silva",
        Set.of("carlos", "alberto", "batista", "ginecologia", "gineco")
    ),
    DR_EDSON_DELFRATE(
        "Dr. Edson Delfrate",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dr. Edson Delfrate",
        Set.of("edson", "delfrate", "ginecologia", "gineco")
    ),
    DR_EDUARDO_SERMAN(
        "Dr. Eduardo Serman",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dr. Eduardo Serman",
        Set.of("eduardo", "serman", "ginecologia", "gineco")
    ),
    DRA_ISABELA_MONGRUEL(
        "Dra. Isabela Baumel Mongruel",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dra. Isabela Baumel Mongruel",
        Set.of("isabela", "baumel", "mongruel", "ginecologia", "gineco")
    ),
    DRA_LISA_TEIXEIRA(
        "Dra. Lisa Paula Fernandes Teixeira",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dra. Lisa Paula Fernandes Teixeira",
        Set.of("lisa", "paula", "fernandes", "teixeira", "ginecologia", "gineco")
    ),
    DRA_TATYELLEN_DALZOTTO(
        "Dra. Tatyellen Dalzotto",
        "Ginecologia",
        "EXTERNAL_WA",
        "Ginecologia - Dra. Tatyellen Dalzotto",
        Set.of("tatyellen", "dalzotto", "ginecologia", "gineco")
    ),

    // 16. Hepatologia
    DR_FILIPE_JUSTUS(
        "Dr. Filipe Fernandes Justus",
        "Hepatologia",
        "EXTERNAL_WA",
        "Hepatologia - Dr. Filipe Fernandes Justus",
        Set.of("filipe", "fernandes", "justus", "hepatologia", "hepato")
    ),

    // 17. Nefrologia
    DR_JOAO_FELIPE_BUENO(
        "Dr. João Felipe Lara Bueno",
        "Nefrologia",
        "DESK",
        "Nefrologia - Dr. João Felipe Lara Bueno",
        Set.of("joao", "felipe", "lara", "bueno", "nefrologia", "nefro")
    ),

    // 18. Neurologia
    DR_CARLOS_CAMARGO(
        "Dr. Carlos Henrique Ferreira Camargo",
        "Neurologia",
        "DESK",
        "Neurologia - Dr. Carlos Henrique Ferreira Camargo",
        Set.of("carlos", "henrique", "camargo", "neurologia", "neuro")
    ),
    DR_MARCELO_TESSARI(
        "Dr. Marcelo Tessari",
        "Neurologia",
        "DESK",
        "Neurologia - Dr. Marcelo Tessari",
        Set.of("marcelo", "tessari", "neurologia", "neuro")
    ),

    // 19. Nutrição
    PAOLA_PAVLAK(
        "Paola Francielle Pavlak",
        "Nutrição",
        "DESK",
        "Nutrição - Paola Francielle Pavlak",
        Set.of("paola", "francielle", "pavlak", "nutricao", "nutricionista", "nutri")
    ),

    // 20. Odontologia
    DR_ROBERTO_KRAVCHYCHYN(
        "Dr. Roberto Kravchychyn",
        "Odontologia",
        "DESK",
        "Odontologia - Dr. Roberto Kravchychyn",
        Set.of("roberto", "kravchychyn", "odontologia", "odonto", "dentista")
    ),

    // 21. Oftalmologia (WhatsApp Próprio Cenovicz)
    DRA_FERNANDA_CENOVICZ(
        "Dra. Fernanda Cenovicz",
        "Oftalmologia",
        "EXTERNAL_WA",
        "Oftalmologia - Dra. Fernanda Cenovicz",
        Set.of("fernanda", "cenovicz", "oftalmologia", "oftalmo")
    ),
    DR_MARCELO_CENOVICZ(
        "Dr. Marcelo Cenovicz",
        "Oftalmologia",
        "EXTERNAL_WA",
        "Oftalmologia - Dr. Marcelo Cenovicz",
        Set.of("marcelo", "cenovicz", "oftalmologia", "oftalmo")
    ),
    DR_MURILO_CENOVICZ(
        "Dr. Murilo Cenovicz",
        "Oftalmologia",
        "EXTERNAL_WA",
        "Oftalmologia - Dr. Murilo Cenovicz",
        Set.of("murilo", "cenovicz", "oftalmologia", "oftalmo")
    ),

    // 22. Ortopedia
    DR_CARLOS_MIERS(
        "Dr. Carlos Miers",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Carlos Miers",
        Set.of("carlos", "miers", "ortopedia", "ortopedista", "orto")
    ),
    DR_CRISTIANO_GATELLI(
        "Dr. Cristiano Gatelli",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Cristiano Gatelli",
        Set.of("cristiano", "gatelli", "ortopedia", "ortopedista", "orto")
    ),
    DR_DANIEL_CARTELLI(
        "Dr. Daniel Cartelli",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Daniel Cartelli",
        Set.of("daniel", "cartelli", "ortopedia", "ortopedista", "orto")
    ),
    DR_FRANKLIN_HILGEMBERG(
        "Dr. Franklin Roberto Hilgemberg",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Franklin Roberto Hilgemberg",
        Set.of("franklin", "roberto", "hilgemberg", "ortopedia", "ortopedista", "orto")
    ),
    DR_LUIS_FELIPE_VILLAS_BOAS(
        "Dr. Luis Felipe Villas Bôas",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Luis Felipe Villas Bôas",
        Set.of("luis", "felipe", "villas", "boas", "ortopedia", "ortopedista", "orto")
    ),
    DRA_MARINA_POLYDORO(
        "Dra. Marina Polydoro",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dra. Marina Polydoro",
        Set.of("marina", "polydoro", "ortopedia", "ortopedista", "orto")
    ),
    DR_RAFAEL_BIAGGI(
        "Dr. Rafael Pançan de Biaggi",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Rafael Pançan de Biaggi",
        Set.of("rafael", "pancan", "biaggi", "ortopedia", "ortopedista", "orto")
    ),
    DR_RODRIGO_FAVARO(
        "Dr. Rodrigo Caldonazzo Fávaro",
        "Ortopedia",
        "DESK",
        "Ortopedia - Dr. Rodrigo Caldonazzo Fávaro",
        Set.of("rodrigo", "caldonazzo", "favaro", "ortopedia", "ortopedista", "orto")
    ),

    // 23. Ortopedia Pediátrica
    DR_EDUARDO_MATTOS(
        "Dr. Eduardo Mattos",
        "Ortopedia Pediátrica",
        "DESK",
        "Ortopedia Pediátrica - Dr. Eduardo Mattos",
        Set.of("eduardo", "mattos", "ortopedia", "pediatrica")
    ),

    // 24. Pediatria
    DRA_FABIOLA_BAIGORRIA(
        "Dra. Fabíola Moreira Baigorria",
        "Pediatria",
        "DESK",
        "Pediatria - Dra. Fabíola Moreira Baigorria",
        Set.of("fabiola", "moreira", "baigorria", "pediatria", "pediatra")
    ),

    // 25. Psicologia
    DRA_THAIS_SILVESTRE(
        "Dra. Thais Fernanda Silvestre",
        "Psicologia",
        "DESK",
        "Psicologia - Dra. Thais Fernanda Silvestre",
        Set.of("thais", "fernanda", "silvestre", "psicologia", "psicologa")
    ),

    // 26. Psiquiatria
    DRA_KELLY_COSTA(
        "Dra. Kelly Melina Brito Costa",
        "Psiquiatria",
        "DESK",
        "Psiquiatria - Dra. Kelly Melina Brito Costa",
        Set.of("kelly", "melina", "brito", "costa", "psiquiatria", "psiquiatra")
    ),

    // 27. Reumatologia
    DR_MARCELO_SCHAFRANSKI(
        "Dr. Marcelo Schafranski",
        "Reumatologia",
        "EXTERNAL_WA",
        "Reumatologia - Dr. Marcelo Schafranski",
        Set.of("marcelo", "schafranski", "reumatologia", "reumato")
    ),

    // 28. Urologia
    DR_ALISSON_FUCIO(
        "Dr. Alisson Vinicius Emerique Fucio",
        "Urologia",
        "DESK",
        "Urologia - Dr. Alisson Vinicius Emerique Fucio",
        Set.of("alisson", "vinicius", "fucio", "urologia", "uro")
    ),
    DR_KOGA(
        "Dr. Carlos Heidi Koga",
        "Urologia",
        "DESK",
        "Urologia - Dr. Carlos Heidi Koga",
        Set.of("carlos", "heidi", "koga", "urologia", "uro")
    ),
    DR_EDUARDO_BISINELLA(
        "Dr. Eduardo Bisinella",
        "Urologia",
        "DESK",
        "Urologia - Dr. Eduardo Bisinella",
        Set.of("eduardo", "bisinella", "urologia", "uro")
    ),
    DR_RICARDO_JECZMIONSKI(
        "Dr. Ricardo Angelo Jeczmionski",
        "Urologia",
        "DESK",
        "Urologia - Dr. Ricardo Angelo Jeczmionski",
        Set.of("ricardo", "angelo", "jeczmionski", "urologia", "uro")
    ),

    // Exames Suportados
    EXAME_TESTE_ERGOMETRICO(
        "Teste Ergométrico",
        "Exames de Cardiologia",
        "DESK",
        "Exames - Teste Ergométrico",
        Set.of("ergometrico", "esteira", "teste")
    ),
    EXAMES_DIGESTIVOS(
        "Endoscopia / Colonoscopia",
        "Exames Digestivos",
        "DESK",
        "Exames Digestivos - Endoscopia / Colonoscopia",
        Set.of("endoscopia", "colonoscopia", "phmetria", "manometria")
    ),
    EXAME_ESPIROMETRIA(
        "Espirometria",
        "Exames Pulmonares",
        "DESK",
        "Exames - Espirometria",
        Set.of("espirometria", "sopro", "pulmao")
    ),
    EXAME_MAPA_HOLTER(
        "MAPA e Holter",
        "Exames de Cardiologia",
        "DESK",
        "Exames - MAPA e Holter",
        Set.of("mapa", "holter", "pressao")
    ),
    EXAME_PHMETRIA_MANOMETRIA(
        "PHmetria e Manometria Esofágica",
        "Exames Digestivos",
        "DESK",
        "Exames Digestivos - PHmetria e Manometria",
        Set.of("phmetria", "manometria", "esofagica")
    ),
    EXAMES_IMAGEM(
        "Clínica da Imagem / Clinipon",
        "Exames de Imagem",
        "EXTERNAL_WA",
        "Exames de Imagem - Clínica da Imagem / Clinipon",
        Set.of("imagem", "clinipon", "ecocardiograma", "eletrocardiograma", "ultrassom", "tomografia")
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
