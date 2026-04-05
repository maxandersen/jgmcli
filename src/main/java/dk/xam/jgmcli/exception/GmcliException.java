package dk.xam.jgmcli.exception;

public class GmcliException extends RuntimeException {
    public GmcliException(String message) {
        super(message);
    }

    public GmcliException(String message, Throwable cause) {
        super(message, cause);
    }
}
