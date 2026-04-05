package dk.xam.jgmcli.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.xam.jgmcli.model.GmailAccount;
import dk.xam.jgmcli.model.Credentials;
import dk.xam.jgmcli.model.Credentials.CredentialsStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AccountStorage {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".jgmcli");
    private static final Path ACCOUNTS_FILE = CONFIG_DIR.resolve("accounts.json");
    private static final Path CREDENTIALS_FILE = CONFIG_DIR.resolve("credentials.json");

    private final Map<String, GmailAccount> accounts = new ConcurrentHashMap<>();
    private CredentialsStore credentialsStore;

    @Inject
    ObjectMapper mapper;

    @PostConstruct
    void init() {
        ensureConfigDir();
        loadAccounts();
        loadCredentials();
    }

    private void ensureConfigDir() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory: " + CONFIG_DIR, e);
        }
    }

    private void loadAccounts() {
        if (Files.exists(ACCOUNTS_FILE)) {
            try {
                List<GmailAccount> loaded = mapper.readValue(
                    ACCOUNTS_FILE.toFile(),
                    new TypeReference<List<GmailAccount>>() {}
                );
                for (GmailAccount account : loaded) {
                    accounts.put(account.email(), account);
                }
            } catch (IOException e) {
                // Ignore corrupt file
            }
        }
    }

    private void saveAccounts() {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(ACCOUNTS_FILE.toFile(), new ArrayList<>(accounts.values()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save accounts", e);
        }
    }

    private void loadCredentials() {
        credentialsStore = new CredentialsStore();
        
        if (!Files.exists(CREDENTIALS_FILE)) {
            return;
        }
        
        try {
            // Try to read as new format (map of named credentials)
            Map<String, Credentials> loaded = mapper.readValue(
                CREDENTIALS_FILE.toFile(),
                new TypeReference<Map<String, Credentials>>() {}
            );
            
            // Check if it's actually old format (has clientId at top level)
            // by seeing if any entry has null clientId (would happen if parsed wrong)
            boolean isNewFormat = loaded.values().stream()
                .allMatch(c -> c != null && c.clientId() != null);
            
            if (isNewFormat && !loaded.isEmpty()) {
                credentialsStore = new CredentialsStore(loaded);
                return;
            }
        } catch (IOException e) {
            // Not new format, try old format
        }
        
        // Try old format (flat clientId/clientSecret or Google format)
        try {
            Credentials oldCreds = readOldCredentialsFormat();
            if (oldCreds != null) {
                credentialsStore.put(Credentials.DEFAULT_NAME, oldCreds);
                // Migrate to new format
                saveCredentials();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private Credentials readOldCredentialsFormat() throws IOException {
        // Try our simple format first
        Credentials creds = mapper.readValue(CREDENTIALS_FILE.toFile(), Credentials.class);
        if (creds.clientId() != null && creds.clientSecret() != null) {
            return creds;
        }
        
        // Try Google's format
        Credentials.GoogleCredentialsFile googleFormat = mapper.readValue(
            CREDENTIALS_FILE.toFile(),
            Credentials.GoogleCredentialsFile.class
        );
        return Credentials.fromGoogleFormat(googleFormat);
    }

    private void saveCredentials() {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(CREDENTIALS_FILE.toFile(), credentialsStore.all());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save credentials", e);
        }
    }

    // Account methods

    public void addAccount(GmailAccount account) {
        accounts.put(account.email(), account);
        saveAccounts();
    }

    public GmailAccount getAccount(String email) {
        return accounts.get(email);
    }

    public List<GmailAccount> getAllAccounts() {
        return new ArrayList<>(accounts.values());
    }

    public boolean deleteAccount(String email) {
        GmailAccount removed = accounts.remove(email);
        if (removed != null) {
            saveAccounts();
            return true;
        }
        return false;
    }

    public boolean hasAccount(String email) {
        return accounts.containsKey(email);
    }

    // Credentials methods

    public void setCredentials(String name, String clientId, String clientSecret) {
        credentialsStore.put(name, new Credentials(clientId, clientSecret));
        saveCredentials();
    }

    public Credentials getCredentials(String name) {
        return credentialsStore.get(name);
    }

    public CredentialsStore getAllCredentials() {
        return credentialsStore;
    }

    public boolean removeCredentials(String name) {
        if (credentialsStore.remove(name)) {
            saveCredentials();
            return true;
        }
        return false;
    }

    public boolean hasCredentials(String name) {
        return credentialsStore.has(name);
    }

    // Legacy methods for backward compatibility
    
    public void setCredentials(String clientId, String clientSecret) {
        setCredentials(Credentials.DEFAULT_NAME, clientId, clientSecret);
    }

    public Credentials getCredentials() {
        return credentialsStore.getDefault();
    }

    // Get attachments directory
    public Path getAttachmentsDir() {
        Path attachmentsDir = CONFIG_DIR.resolve("attachments");
        try {
            if (!Files.exists(attachmentsDir)) {
                Files.createDirectories(attachmentsDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create attachments directory", e);
        }
        return attachmentsDir;
    }
}
