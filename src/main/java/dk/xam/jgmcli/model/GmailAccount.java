package dk.xam.jgmcli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailAccount(
    String email,
    String credentialsName,  // null = default credentials
    OAuth2Credentials oauth2
) {
    // Constructor for backward compatibility (no credentialsName)
    public GmailAccount(String email, OAuth2Credentials oauth2) {
        this(email, null, oauth2);
    }
}
