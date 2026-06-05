package br.dev.ctrls.inovareti.core.shared.domain.model.exception;

public class FileSizeLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileSizeLimitExceededException(String message) {
        super(message);
    }
}
