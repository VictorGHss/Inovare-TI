package br.dev.ctrls.inovareti.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class FileSizeLimitExceededException extends RuntimeException {

    public FileSizeLimitExceededException(String message) {
        super(message);
    }
}
