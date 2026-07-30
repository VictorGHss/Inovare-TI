package br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringSanitizerTest {

    @Test
    @DisplayName("Deve auto-corrigir número de 10 dígitos (DDD + 8 dígitos) adicionando o 9º dígito")
    void testAutoCorrection9thDigit() {
        String result = StringSanitizer.formatE164("4288044290");
        assertEquals("+5542988044290", result, "Deve inserir o 9º dígito após o DDD 42");
    }

    @Test
    @DisplayName("Deve remover DDI 55 duplicado e auto-corrigir número de 12 dígitos")
    void testRemove55AndAutoCorrect() {
        String result = StringSanitizer.formatE164("554288044290");
        assertEquals("+5542988044290", result, "Deve remover 55 e inserir o 9º dígito");
    }

    @Test
    @DisplayName("Deve aceitar número já correto de 13 dígitos com DDI 55")
    void testValid13Digits() {
        String result = StringSanitizer.formatE164("5542988044290");
        assertEquals("+5542988044290", result, "Deve formatar número de 13 dígitos para E.164");
    }

    @Test
    @DisplayName("Deve descartar número fixo sem DDD com menos de 10 dígitos")
    void testDiscardShortNumberWithoutDDD() {
        String result = StringSanitizer.formatE164("32351298");
        assertNull(result, "Número de 8 dígitos sem DDD deve ser descartado (retornar null)");
    }

    @Test
    @DisplayName("Deve descartar telefone fixo (10 dígitos iniciando por 2, 3, 4 ou 5 após o DDD)")
    void testDiscardLandlinePhone() {
        String result = StringSanitizer.formatE164("4232351298");
        assertNull(result, "Telefone fixo com DDD deve ser descartado (retornar null)");
    }

    @Test
    @DisplayName("Deve descartar telefone nulo ou em branco")
    void testNullOrBlank() {
        assertNull(StringSanitizer.formatE164(null));
        assertNull(StringSanitizer.formatE164(""));
        assertNull(StringSanitizer.formatE164("   "));
        assertNull(StringSanitizer.formatE164("null"));
    }
}
