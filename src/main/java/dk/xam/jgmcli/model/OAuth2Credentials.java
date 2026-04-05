package dk.xam.jgmcli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuth2Credentials(
    String clientId,
    String clientSecret,
    String refreshToken,
    String accessToken
) {
    public OAuth2Credentials(String clientId, String clientSecret, String refreshToken) {
        this(clientId, clientSecret, refreshToken, null);
    }
}
