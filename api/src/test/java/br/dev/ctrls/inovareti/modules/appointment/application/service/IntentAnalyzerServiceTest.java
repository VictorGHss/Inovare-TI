package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.DoctorMatchDto;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResultDto;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.DateParserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntentAnalyzerServiceTest {

    private IntentAnalyzerService intentAnalyzerService;

    @BeforeEach
    void setUp() {
        intentAnalyzerService = new IntentAnalyzerService();
    }

    @Test
    @DisplayName("Deve extrair especialidade a partir de gíria 'gineco' e filtrar stop-words")
    void testAnalyzeIntentGineco() {
        IntentAnalysisResultDto result = intentAnalyzerService.analyzeIntent("Olá, gostaria de agendar consulta com gineco por favor");

        assertNotNull(result);
        assertEquals("Ginecologia", result.getExtractedSpecialty());
        assertFalse(result.getMatches().isEmpty());
        assertTrue(result.getMatches().stream().anyMatch(m -> "Ginecologia".equalsIgnoreCase(m.getSpecialty())));
    }

    @Test
    @DisplayName("Deve identificar desambiguação para buscas genéricas por termo 'Carlos'")
    void testAnalyzeIntentAmbiguityCarlos() {
        IntentAnalysisResultDto result = intentAnalyzerService.analyzeIntent("Quero consulta com Dr. Carlos");

        assertNotNull(result);
        assertTrue(result.isHasAmbiguity());
        assertEquals("DESAMBIGUACAO", result.getIntent());
        assertTrue(result.getMatches().size() >= 1);
    }

    @Test
    @DisplayName("Deve paginar resultados e injetar o 10º item sintético 'Ver mais médicos...' se houver mais de 10 opções")
    void testPaginationWithSyntheticItem() {
        // Busca genérica "Cirurgia" ou "Ortopedia" que retorna múltiplos médicos
        IntentAnalysisResultDto page1 = intentAnalyzerService.analyzeIntent("Cirurgia", 1);

        assertNotNull(page1);
        if (page1.getTotalMatches() > 10) {
            assertTrue(page1.isHasNextPage());
            assertEquals(1, page1.getPage());
            assertEquals(10, page1.getMatches().size()); // 9 médicos reais + 1 sintético

            DoctorMatchDto lastMatch = page1.getMatches().get(9);
            assertTrue(Boolean.TRUE.equals(lastMatch.getIsSynthetic()));
            assertEquals("Ver mais médicos...", lastMatch.getDoctorName());
            assertEquals(2, lastMatch.getNextPage());

            // Testar busca da página 2
            IntentAnalysisResultDto page2 = intentAnalyzerService.analyzeIntent("Cirurgia", 2);
            assertNotNull(page2);
            assertEquals(2, page2.getPage());
            assertFalse(page2.getMatches().isEmpty());
        } else {
            assertEquals(page1.getTotalMatches(), page1.getMatches().size());
        }
    }

    @Test
    @DisplayName("Deve converter datas em múltiplos formatos para o padrão ISO (YYYY-MM-DD)")
    void testDateParserUtils() {
        assertEquals("1990-08-15", DateParserUtils.parseToIsoDate("15/08/1990"));
        assertEquals("1990-08-15", DateParserUtils.parseToIsoDate("15-08-1990"));
        assertEquals("1990-08-15", DateParserUtils.parseToIsoDate("15.08.1990"));
        assertEquals("1990-08-15", DateParserUtils.parseToIsoDate("15 08 1990"));
        assertEquals("1990-08-15", DateParserUtils.parseToIsoDate("1990-08-15"));
    }
}
