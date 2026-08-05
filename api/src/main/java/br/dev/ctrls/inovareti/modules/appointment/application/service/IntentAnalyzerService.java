package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.DoctorMatchDto;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResultDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorCatalog;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Serviço de NLP leve responsável pela análise de mensagens de pacientes,
 * filtragem de stop-words/gírias, normalização de especialidades, desambiguação e paginação
 * para o limite de 10 opções da Lista Interativa do WhatsApp.
 */
@Slf4j
@Service
@Observed
public class IntentAnalyzerService {

    private static final int PAGE_SIZE = 9; // 9 médicos + 1 botão sintético "Ver mais médicos..." (máx 10 itens WhatsApp)
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Set<String> STOP_WORDS = Set.of(
            "ola", "olaa", "oi", "bom", "dia", "boa", "tarde", "noite",
            "agendar", "agendamento", "consulta", "marcar", "com", "quero",
            "por", "favor", "gostaria", "de", "para", "o", "a", "os", "as",
            "um", "uma", "dr", "dra", "doutor", "doutora", "medico", "medica",
            "preciso", "ver", "passar", "atendimento", "clinica"
    );

    private static final Map<String, String> NICKNAME_SPECIALTY_MAP = Map.ofEntries(
            Map.entry("gineco", "Ginecologia"),
            Map.entry("ginecologista", "Ginecologia"),
            Map.entry("oftalmo", "Oftalmologia"),
            Map.entry("oftalmologista", "Oftalmologia"),
            Map.entry("cardio", "Cardiologia"),
            Map.entry("cardiologista", "Cardiologia"),
            Map.entry("uro", "Urologia"),
            Map.entry("urologista", "Urologia"),
            Map.entry("pneumo", "Cirurgia Torácica e Pneumologia"),
            Map.entry("pneumologista", "Cirurgia Torácica e Pneumologia"),
            Map.entry("reumato", "Reumatologia"),
            Map.entry("reumatologista", "Reumatologia"),
            Map.entry("imuno", "Alergia e Imunologia"),
            Map.entry("alergista", "Alergia e Imunologia"),
            Map.entry("digestivo", "Cirurgia do Aparelho Digestivo"),
            Map.entry("plastica", "Cirurgia Plástica"),
            Map.entry("vascular", "Cirurgia Vascular"),
            Map.entry("toracica", "Cirurgia Torácica e Pneumologia"),
            Map.entry("dermato", "Dermatologia"),
            Map.entry("dermatologista", "Dermatologia"),
            Map.entry("orto", "Ortopedia"),
            Map.entry("ortopedista", "Ortopedia")
    );

    /**
     * Analisa o texto bruto do paciente utilizando a página padrão (página 1).
     */
    public IntentAnalysisResultDto analyzeIntent(String rawInput) {
        return analyzeIntent(rawInput, 1);
    }

    /**
     * Analisa o texto bruto do paciente com suporte a paginação de resultados.
     *
     * @param rawInput Mensagem digitada pelo paciente no WhatsApp
     * @param page Número da página solicitada (1-based)
     * @return IntentAnalysisResultDto contendo intenção, especialidade, médicos e metadados de paginação
     */
    public IntentAnalysisResultDto analyzeIntent(String rawInput, int page) {
        if (rawInput == null || rawInput.isBlank()) {
            return IntentAnalysisResultDto.builder()
                    .rawInput(rawInput)
                    .cleanedInput("")
                    .intent("NAO_RECONHECIDO")
                    .hasAmbiguity(false)
                    .page(1)
                    .pageSize(PAGE_SIZE)
                    .totalMatches(0)
                    .totalPages(0)
                    .hasNextPage(false)
                    .matches(Collections.emptyList())
                    .build();
        }

        String normalized = stripAccents(rawInput.toLowerCase().trim());
        List<String> tokens = Arrays.stream(normalized.split("[^a-zA-Z0-9]+"))
                .filter(t -> !t.isBlank())
                .filter(t -> !STOP_WORDS.contains(t))
                .collect(Collectors.toList());

        String cleanedText = String.join(" ", tokens);
        log.info("[INTENT-ANALYZER] Entrada: '{}' | Higienizado: '{}' | Página: {}", rawInput, cleanedText, page);

        // Mapear gírias/apelidos de especialidade
        String mappedSpecialty = resolveSpecialtyNickname(tokens);

        // Buscar médicos correspondentes no DoctorCatalog
        List<DoctorCatalog> matchedCatalogs = findMatchingDoctorCatalogs(tokens, mappedSpecialty);

        List<DoctorMatchDto> allMatchDtos = matchedCatalogs.stream()
                .map(this::toDoctorMatchDto)
                .collect(Collectors.toList());

        int totalMatches = allMatchDtos.size();
        boolean hasAmbiguity = totalMatches > 1;
        String intent;
        if (totalMatches == 0) {
            intent = "NAO_RECONHECIDO";
        } else if (hasAmbiguity) {
            intent = "DESAMBIGUACAO";
        } else {
            intent = "AGENDAMENTO";
        }

        // Lógica de Paginação (Limite de 10 itens do WhatsApp Interactive List)
        int targetPage = Math.max(1, page);
        int totalPages = totalMatches <= 10 ? 1 : (int) Math.ceil((double) totalMatches / PAGE_SIZE);
        int currentPage = Math.min(targetPage, Math.max(1, totalPages));

        List<DoctorMatchDto> paginatedMatches;
        boolean hasNextPage;

        if (totalMatches <= 10) {
            paginatedMatches = new ArrayList<>(allMatchDtos);
            hasNextPage = false;
        } else {
            int startIndex = (currentPage - 1) * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, totalMatches);
            paginatedMatches = new ArrayList<>(allMatchDtos.subList(startIndex, endIndex));
            hasNextPage = currentPage < totalPages;

            if (hasNextPage) {
                // Injeta o 10º item sintético "Ver mais médicos..."
                paginatedMatches.add(DoctorMatchDto.builder()
                        .doctorName("Ver mais médicos...")
                        .specialty(mappedSpecialty != null ? mappedSpecialty : "Mais opções")
                        .isInternal(true)
                        .route("PAGINATION")
                        .isSynthetic(true)
                        .nextPage(currentPage + 1)
                        .build());
            }
        }

        return IntentAnalysisResultDto.builder()
                .rawInput(rawInput)
                .cleanedInput(cleanedText)
                .intent(intent)
                .extractedSpecialty(mappedSpecialty)
                .hasAmbiguity(hasAmbiguity)
                .page(currentPage)
                .pageSize(PAGE_SIZE)
                .totalMatches(totalMatches)
                .totalPages(totalPages)
                .hasNextPage(hasNextPage)
                .matches(paginatedMatches)
                .build();
    }

    private String resolveSpecialtyNickname(List<String> tokens) {
        for (String token : tokens) {
            if (NICKNAME_SPECIALTY_MAP.containsKey(token)) {
                return NICKNAME_SPECIALTY_MAP.get(token);
            }
        }
        return null;
    }

    private List<DoctorCatalog> findMatchingDoctorCatalogs(List<String> tokens, String mappedSpecialty) {
        Set<DoctorCatalog> matches = new LinkedHashSet<>();

        for (DoctorCatalog catalog : DoctorCatalog.values()) {
            // Se especialidade foi mapeada, checa igualdade
            if (mappedSpecialty != null && catalog.getSpecialty().equalsIgnoreCase(mappedSpecialty)) {
                matches.add(catalog);
                continue;
            }

            // Checa interseção de tokens
            for (String token : tokens) {
                if (token.length() >= 3 && catalog.getTokens().contains(token)) {
                    matches.add(catalog);
                }
            }
        }

        return new ArrayList<>(matches);
    }

    private DoctorMatchDto toDoctorMatchDto(DoctorCatalog catalog) {
        boolean isInternal = "DESK".equalsIgnoreCase(catalog.getRoute());
        return DoctorMatchDto.builder()
                .doctorName(catalog.getDoctorName())
                .specialty(catalog.getSpecialty())
                .route(catalog.getRoute())
                .queue(catalog.getQueue())
                .isInternal(isInternal)
                .externalLink(!isInternal ? "https://wa.me/" : null)
                .externalPhone(!isInternal ? "5542999999999" : null)
                .isSynthetic(false)
                .build();
    }

    private String stripAccents(String src) {
        if (src == null) return "";
        String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }
}
