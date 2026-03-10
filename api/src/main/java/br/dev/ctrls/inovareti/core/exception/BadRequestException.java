package br.dev.ctrls.inovareti.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exceção lançada quando uma requisição é inválida ou contém dados incorretos.
 * Mapeada automaticamente para resposta HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    /**
     * Constrói uma nova BadRequestException com a mensagem de erro especificada.
     *
     * @param message a mensagem de detalhe
     */
    public BadRequestException(String message) {
        super(message);
    }
}
