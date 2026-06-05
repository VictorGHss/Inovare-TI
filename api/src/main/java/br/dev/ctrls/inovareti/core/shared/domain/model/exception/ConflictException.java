package br.dev.ctrls.inovareti.core.shared.domain.model.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Lançada quando uma operação viola uma restrição de unicidade
 * (ex.: nome duplicado). Resulta em HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
