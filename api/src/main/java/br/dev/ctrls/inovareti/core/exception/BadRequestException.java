package br.dev.ctrls.inovareti.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a request is invalid or contains bad data.
 * Automatically maps to HTTP 400 Bad Request response.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    /**
     * Constructs a new BadRequestException with the specified error message.
     *
     * @param message the detail message
     */
    public BadRequestException(String message) {
        super(message);
    }
}
