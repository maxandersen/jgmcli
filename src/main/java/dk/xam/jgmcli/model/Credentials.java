package dk.xam.jgmcli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Credentials(
    String clientId,
    String clientSecret
) {
    public static final String DEFAULT_NAME = "default";

    /**
     * Factory method to parse Google's download format (has "installed" or "web" wrapper)
     */
    public static Credentials fromGoogleFormat(GoogleCredentialsFile file) {
        if (file.installed() != null) {
            return new Credentials(file.installed().clientId(), file.installed().clientSecret());
        } else if (file.web() != null) {
            return new Credentials(file.web().clientId(), file.web().clientSecret());
        } else if (file.clientId() != null && file.clientSecret() != null) {
            return new Credentials(file.clientId(), file.clientSecret());
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleCredentialsFile(
        InstalledCredentials installed,
        InstalledCredentials web,
        String clientId,
        String clientSecret
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstalledCredentials(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_secret") String clientSecret
    ) {}

    /**
     * Store for multiple named credentials
     */
    public static class CredentialsStore {
        private final Map<String, Credentials> credentials;

        public CredentialsStore() {
            this.credentials = new LinkedHashMap<>();
        }

        public CredentialsStore(Map<String, Credentials> credentials) {
            this.credentials = new LinkedHashMap<>(credentials);
        }

        public Credentials get(String name) {
            return credentials.get(name != null ? name : DEFAULT_NAME);
        }

        public Credentials getDefault() {
            return credentials.get(DEFAULT_NAME);
        }

        public void put(String name, Credentials creds) {
            credentials.put(name != null ? name : DEFAULT_NAME, creds);
        }

        public boolean remove(String name) {
            return credentials.remove(name) != null;
        }

        public Set<String> names() {
            return Collections.unmodifiableSet(credentials.keySet());
        }

        public Map<String, Credentials> all() {
            return Collections.unmodifiableMap(credentials);
        }

        public boolean isEmpty() {
            return credentials.isEmpty();
        }

        public boolean has(String name) {
            return credentials.containsKey(name != null ? name : DEFAULT_NAME);
        }
    }
}
