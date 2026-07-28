package br.dev.ctrls.inovareti.modules.appointment.domain.exception;

public class BlipNotificationException extends RuntimeException {
    public BlipNotificationException(String message) {
        super(message);
    }

    public BlipNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
