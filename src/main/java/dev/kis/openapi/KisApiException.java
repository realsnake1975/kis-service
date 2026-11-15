package dev.kis.openapi;

public class KisApiException extends RuntimeException {
    private final String code;

    public KisApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public KisApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public String getCode() { return code; }
}
