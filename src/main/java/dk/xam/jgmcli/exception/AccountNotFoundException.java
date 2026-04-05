package dk.xam.jgmcli.exception;

public class AccountNotFoundException extends GmcliException {
    public AccountNotFoundException(String email) {
        super("Account '" + email + "' not found");
    }
}
