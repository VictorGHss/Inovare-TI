package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serviço de aplicação IntentAnalysisService.
 * Realiza normalização de texto, filtragem de stopwords, mapeamento de sinônimos/apelidos
 * e busca por relevância para extração de intenções do bot Blip.
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
    private static final Set<String> HIGH_WEIGHT_SURNAMES = Set.of(
        "koga", "solak", "saad", "tessari", "gulin", "doretto", "acuna", "ferreira",
        "sirtoli", "phmetria", "colonoscopia", "endoscopia", "ecocardiograma", "holter", "mapa", "clinipon"
    );

    static {
        SYNONYMS.put("dermato", List.of("dermatologia", "dermato"));
        SYNONYMS.put("gineco", List.of("ginecologia", "gineco"));
        SYNONYMS.put("obstetra", List.of("ginecologia", "obstetra"));
        SYNONYMS.put("uro", List.of("urologia", "uro"));
        SYNONYMS.put("koga", List.of("urologia", "koga"));
        SYNONYMS.put("oftalmo", List.of("oftalmologia", "oftalmo"));
        SYNONYMS.put("odonto", List.of("odontologia", "odonto"));
        SYNONYMS.put("dentista", List.of("odontologia", "dentista"));
        SYNONYMS.put("imuno", List.of("alergia", "imunologia", "imuno"));
        SYNONYMS.put("pediatra", List.of("pediatria", "pediatra"));
        SYNONYMS.put("cardio", List.of("cardiologia", "cardio"));
        SYNONYMS.put("gastro", List.of("gastroenterologia", "gastro"));
        SYNONYMS.put("neuro", List.of("neurologia", "neuro"));
    }

    /**
     * Processa a mensagem de entrada do usuário e determina a intenção ou o médico/especialidade buscado.
     *
     * @param request DTO de requisição contendo a propriedade "mensagem".
     * @return IntentAnalysisResponse com o contrato de resposta exigido pelo Blip.
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

        // 2. Normalização do Texto: NFD (remoção de acentos), minúsculas e remoção de pontuação
        String normalized = Normalizer.normalize(rawInput.toLowerCase(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        if (normalized.isEmpty()) {
            return IntentAnalysisResponse.builder()
                .tipo("NENHUM_RESULTADO")
                .termoBuscado(rawInput)
                .build();
        }

        // 3. Filtragem de Stopwords
        String[] words = normalized.split(" ");
        List<String> relevantWords = new ArrayList<>();
        for (String w : words) {
            if (!w.isBlank() && !STOPWORDS.contains(w)) {
                relevantWords.add(w);
            }
        }

        if (relevantWords.isEmpty()) {
            log.info("[IntentAnalysis] Apenas stopwords encontradas no texto: '{}'", normalized);
            return IntentAnalysisResponse.builder()
                .tipo("NENHUM_RESULTADO")
                .termoBuscado(normalized)
                .build();
        }

        // 4. Mapeamento de Sinônimos / Apelidos
        Set<String> searchTokens = new HashSet<>(relevantWords);
        for (String w : relevantWords) {
            if (SYNONYMS.containsKey(w)) {
                searchTokens.addAll(SYNONYMS.get(w));
            }
        }

        // 5. Cálculo de Relevância (Score) na Base de Médicos e Exames
        DoctorCatalog bestMatch = null;
        int maxScore = 0;

        for (DoctorCatalog entry : DoctorCatalog.values()) {
            int score = 0;
            for (String token : searchTokens) {
                if (entry.getTokens().contains(token)) {
                    score += 10;
                    if (HIGH_WEIGHT_SURNAMES.contains(token)) {
                        score += 15;
                    }
                }
            }
            if (score > maxScore) {
                maxScore = score;
                bestMatch = entry;
            }
        }

        String termoBuscado = String.join(" ", relevantWords);

        if (bestMatch != null && maxScore > 0) {
            log.info("[IntentAnalysis] Correspondência encontrada: {} ({}) com score {}. Termo: '{}'", 
                    bestMatch.getDoctorName(), bestMatch.getSpecialty(), maxScore, termoBuscado);
            return IntentAnalysisResponse.builder()
                .tipo("RESULTADOS_BUSCA")
                .termoBuscado(termoBuscado)
                .medico(bestMatch.getDoctorName())
                .especialidade(bestMatch.getSpecialty())
                .fila(bestMatch.getQueue())
                .rota(bestMatch.getRoute())
                .build();
        }

        log.info("[IntentAnalysis] Nenhuma correspondência de médico/especialidade encontrada para: '{}'", termoBuscado);
        return IntentAnalysisResponse.builder()
            .tipo("NENHUM_RESULTADO")
            .termoBuscado(termoBuscado)
            .build();
    }
}
