package br.dev.ctrls.inovareti.core.shared.domain.model.exception;

public class FileSizeLimitExceededException extends RuntimeException {

    public FileSizeLimitExceededException(String message) {
        super(message);
    }
}
