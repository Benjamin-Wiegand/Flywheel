package io.benwiegand.projection.geargrinder.exception;

public class PemParseException extends Exception {
    public PemParseException(String message) {
        super(message);
    }

    public PemParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
