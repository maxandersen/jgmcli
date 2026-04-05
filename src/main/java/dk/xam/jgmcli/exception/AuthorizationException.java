package dk.xam.jgmcli.exception;

public class AuthorizationException extends GmcliException {
    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
