package io.webaa.sdk.exception;

/**
 * Base exception for all WebAA SDK errors.
 */
public class WebAAException extends RuntimeException {

    private final int statusCode;

    public WebAAException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public WebAAException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public WebAAException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
