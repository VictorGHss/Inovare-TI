package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serviço de aplicação IntentAnalysisService.
 * Realiza busca por tokens fatiados, remoção de stopwords/acentos, mapeamento de sinônimos,
 * seleção por índice numérico ("1", "2", "opcao 1") e classificação de intenção
 * ("RESULTADO_UNICO", "MULTIPLOS_RESULTADOS", "TRIGGER_ITSM", "NENHUM_RESULTADO").
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Service
public class IntentAnalysisService {

    private static final Set<String> STOPWORDS = Set.of(
        "ola", "oi", "bom", "dia", "boa", "tarde", "noite", "meu", "nome", "e",
        "gostaria", "de", "falar", "na", "no", "agendar", "marcar", "com", "o", "a",
        "dr", "dra", "doutor", "doutora", "por", "favor", "consulta", "exame", "preciso", "quero", "agendamento"
    );

    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();

    static {
        SYNONYMS.put("dermato", List.of("dermatologia", "dermato"));
        SYNONYMS.put("gineco", List.of("ginecologia", "gineco"));
        SYNONYMS.put("ginecologista", List.of("ginecologia", "gineco"));
        SYNONYMS.put("obstetra", List.of("ginecologia", "obstetra"));
        SYNONYMS.put("uro", List.of("urologia", "uro", "urologista"));
        SYNONYMS.put("urologista", List.of("urologia", "uro", "urologista"));
        SYNONYMS.put("oftalmo", List.of("oftalmologia", "oftalmo", "oftalmologista"));
        SYNONYMS.put("oftalmologista", List.of("oftalmologia", "oftalmo"));
        SYNONYMS.put("odonto", List.of("odontologia", "odonto", "dentista"));
        SYNONYMS.put("dentista", List.of("odontologia", "odonto", "dentista"));
        SYNONYMS.put("imuno", List.of("alergia", "imunologia", "imuno", "alergologista"));
        SYNONYMS.put("alergologista", List.of("alergia", "imunologia", "alergologista"));
        SYNONYMS.put("pediatra", List.of("pediatria", "pediatra"));
        SYNONYMS.put("cardio", List.of("cardiologia", "cardio", "cardiologista"));
        SYNONYMS.put("cardiologista", List.of("cardiologia", "cardio"));
        SYNONYMS.put("gastro", List.of("gastroenterologia", "gastro", "gastroenterologista"));
        SYNONYMS.put("gastroenterologista", List.of("gastroenterologia", "gastro"));
        SYNONYMS.put("neuro", List.of("neurologia", "neuro", "neurologista"));
        SYNONYMS.put("neurologista", List.of("neurologia", "neuro"));
        SYNONYMS.put("nefro", List.of("nefrologia", "nefro", "nefrologista"));
        SYNONYMS.put("nefrologista", List.of("nefrologia", "nefro"));
        SYNONYMS.put("orto", List.of("ortopedia", "ortopedista", "orto"));
        SYNONYMS.put("ortopedista", List.of("ortopedia", "orto"));
        SYNONYMS.put("reumato", List.of("reumatologia", "reumato", "reumatologista"));
        SYNONYMS.put("reumatologista", List.of("reumatologia", "reumato"));
        SYNONYMS.put("pneumo", List.of("pneumologia", "pneumo", "pneumologista"));
        SYNONYMS.put("pneumologista", List.of("pneumologia", "pneumo"));
        SYNONYMS.put("fisio", List.of("fisioterapia", "fisio", "fisioterapeuta"));
        SYNONYMS.put("fisioterapeuta", List.of("fisioterapia", "fisio"));
        SYNONYMS.put("fono", List.of("fonoaudiologia", "fono", "fonoaudiologa"));
        SYNONYMS.put("fonoaudiologa", List.of("fonoaudiologia", "fono"));
        SYNONYMS.put("endocrino", List.of("endocrinologia", "endocrino", "endocrinologista"));
        SYNONYMS.put("endocrinologista", List.of("endocrinologia", "endocrino"));
        SYNONYMS.put("nutri", List.of("nutricao", "nutricionista", "nutri"));
        SYNONYMS.put("nutricionista", List.of("nutricao", "nutri"));
        SYNONYMS.put("psiquiatra", List.of("psiquiatria", "psiquiatra"));
        SYNONYMS.put("psicologa", List.of("psicologia", "psicologa"));
        SYNONYMS.put("ultrassom", List.of("imagem", "ultrassom"));
    }

    /**
     * Processa a mensagem de entrada e retorna o resultado da busca por tokens fatiados ou por seleção numérica.
     *
     * @param request DTO contendo a mensagem do usuário e contexto opcional.
     * @return IntentAnalysisResponse com o tipo apropriado.
     */
    public IntentAnalysisResponse processIntent(IntentAnalysisRequest request) {
        if (request == null || request.getMensagem() == null || request.getMensagem().isBlank()) {
            log.info("[IntentAnalysis] Requisição ou mensagem vazia recebida.");
            return IntentAnalysisResponse.builder()
                .tipo("NENHUM_RESULTADO")
                .termoBuscado("")
                .build();
        }

        String rawInput = request.getMensagem().trim();
        String rawLower = rawInput.toLowerCase();

        // 1. Identificação de Triggers ITSM (confirm_, alter_, ver_agenda_)
        if (rawLower.startsWith("confirm_") || rawLower.startsWith("alter_") || rawLower.startsWith("ver_agenda_")) {
            log.info("[IntentAnalysis] Trigger ITSM detectado: {}", rawInput);
            return IntentAnalysisResponse.builder()
                .tipo("TRIGGER_ITSM")
                .acao(rawInput)
                .build();
        }

        // 2. Tratamento de Seleção por Índice Numérico (ex: "1", "2", "opcao 1")
        Integer numericIndex = parseNumericIndex(rawInput);
        if (numericIndex != null) {
            String contextTerm = (request.getTermoAnterior() != null && !request.getTermoAnterior().isBlank())
                ? request.getTermoAnterior()
                : request.getEspecialidade();

            if (contextTerm != null && !contextTerm.isBlank()) {
                List<DoctorCatalog> topMatches = findTopMatchesForTerm(contextTerm);
                if (numericIndex >= 1 && numericIndex <= topMatches.size()) {
                    DoctorCatalog selected = topMatches.get(numericIndex - 1);
                    log.info("[IntentAnalysis] Seleção por índice numérico {} para contexto '{}': {} ({})",
                            numericIndex, contextTerm, selected.getDoctorName(), selected.getSpecialty());

                    return IntentAnalysisResponse.builder()
                        .tipo("RESULTADO_UNICO")
                        .termoBuscado(contextTerm)
                        .medico(selected.getDoctorName())
                        .especialidade(selected.getSpecialty())
                        .fila(selected.getQueue())
                        .rota(selected.getRoute())
                        .linkWa(buildWaLink(selected))
                        .build();
                }
            }
        }

        // 3. Busca Padrão por Tokens
        List<DoctorCatalog> topMatches = findTopMatchesForTerm(rawInput);
        String termoBuscado = extractTermoBuscado(rawInput);

        if (topMatches.isEmpty()) {
            log.info("[IntentAnalysis] Nenhuma correspondência para os tokens: '{}'", termoBuscado);
            return IntentAnalysisResponse.builder()
                .tipo("NENHUM_RESULTADO")
                .termoBuscado(termoBuscado)
                .build();
        }

        if (topMatches.size() == 1) {
            DoctorCatalog bestMatch = topMatches.get(0);
            log.info("[IntentAnalysis] RESULTADO_UNICO: {} ({}) para termo '{}'",
                    bestMatch.getDoctorName(), bestMatch.getSpecialty(), termoBuscado);

            return IntentAnalysisResponse.builder()
                .tipo("RESULTADO_UNICO")
                .termoBuscado(termoBuscado)
                .medico(bestMatch.getDoctorName())
                .especialidade(bestMatch.getSpecialty())
                .fila(bestMatch.getQueue())
                .rota(bestMatch.getRoute())
                .linkWa(buildWaLink(bestMatch))
                .build();
        } else {
            log.info("[IntentAnalysis] MULTIPLOS_RESULTADOS ({}) para termo '{}'", topMatches.size(), termoBuscado);

            List<IntentAnalysisResponse.DoctorOption> options = topMatches.stream()
                .map(match -> IntentAnalysisResponse.DoctorOption.builder()
                    .medico(match.getDoctorName())
                    .especialidade(match.getSpecialty())
                    .fila(match.getQueue())
                    .rota(match.getRoute())
                    .linkWa(buildWaLink(match))
                    .build())
                .toList();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < options.size(); i++) {
                IntentAnalysisResponse.DoctorOption opt = options.get(i);
                sb.append(i + 1)
                  .append(". ")
                  .append(opt.getMedico())
                  .append(" (")
                  .append(opt.getEspecialidade())
                  .append(")\n");
            }

            return IntentAnalysisResponse.builder()
                .tipo("MULTIPLOS_RESULTADOS")
                .termoBuscado(termoBuscado)
                .opcoes(options)
                .opcoesFormatadas(sb.toString().trim())
                .build();
        }
    }

    private List<DoctorCatalog> findTopMatchesForTerm(String termInput) {
        if (termInput == null || termInput.isBlank()) {
            return List.of();
        }

        String normalized = Normalizer.normalize(termInput.toLowerCase(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] words = normalized.split(" ");
        List<String> relevantWords = new ArrayList<>();
        for (String w : words) {
            if (!w.isBlank() && !STOPWORDS.contains(w)) {
                relevantWords.add(w);
            }
        }

        if (relevantWords.isEmpty()) {
            return List.of();
        }

        Set<String> searchTokens = new HashSet<>(relevantWords);
        for (String w : relevantWords) {
            if (SYNONYMS.containsKey(w)) {
                searchTokens.addAll(SYNONYMS.get(w));
            }
        }

        List<DoctorCatalogScore> matchingCandidates = new ArrayList<>();
        for (DoctorCatalog entry : DoctorCatalog.values()) {
            int score = 0;
            for (String token : searchTokens) {
                if (entry.getTokens().contains(token)) {
                    score += 10;
                }
            }
            if (score > 0) {
                matchingCandidates.add(new DoctorCatalogScore(entry, score));
            }
        }

        if (matchingCandidates.isEmpty()) {
            return List.of();
        }

        matchingCandidates.sort((c1, c2) -> Integer.compare(c2.score(), c1.score()));
        int maxScore = matchingCandidates.get(0).score();

        return matchingCandidates.stream()
            .filter(c -> c.score() == maxScore)
            .map(c -> c.catalog())
            .toList();
    }

    private String extractTermoBuscado(String termInput) {
        String normalized = Normalizer.normalize(termInput.toLowerCase(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        String[] words = normalized.split(" ");
        List<String> relevantWords = new ArrayList<>();
        for (String w : words) {
            if (!w.isBlank() && !STOPWORDS.contains(w)) {
                relevantWords.add(w);
            }
        }
        return relevantWords.isEmpty() ? normalized : String.join(" ", relevantWords);
    }

    private Integer parseNumericIndex(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("[^0-9]", "").trim();
        if (!clean.isEmpty() && clean.length() <= 2) {
            try {
                int val = Integer.parseInt(clean);
                if (val >= 1 && val <= 20) {
                    return val;
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String buildWaLink(DoctorCatalog catalog) {
        try {
            String message = "Olá! Gostaria de agendar atendimento com " + catalog.getDoctorName() + " (" + catalog.getSpecialty() + ").";
            String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8);
            return "https://wa.me/554230262600?text=" + encodedMsg;
        } catch (Exception e) {
            return null;
        }
    }

    private record DoctorCatalogScore(DoctorCatalog catalog, int score) {}
}
