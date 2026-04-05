package dk.xam.jgmcli.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.xam.jgmcli.model.GmailAccount;
import dk.xam.jgmcli.model.Credentials;
import dk.xam.jgmcli.model.Credentials.CredentialsStore;
import dk.xam.jgmcli.service.GmailService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "accounts", description = "Account management",
         mixinStandardHelpOptions = true,
         subcommands = {
             AccountsCommand.ListCommand.class,
             AccountsCommand.CredentialsCommand.class,
             AccountsCommand.AddCommand.class,
             AccountsCommand.RemoveCommand.class
         })
public class AccountsCommand {

    @Command(name = "list", description = "List configured accounts", mixinStandardHelpOptions = true)
    static class ListCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Override
        public Integer call() {
            List<GmailAccount> accounts = service.listAccounts();
            if (accounts.isEmpty()) {
                System.out.println("No accounts configured");
            } else {
                for (GmailAccount account : accounts) {
                    if (account.credentialsName() != null) {
                        System.out.println(account.email() + "\t(" + account.credentialsName() + ")");
                    } else {
                        System.out.println(account.email());
                    }
                }
            }
            return 0;
        }
    }

    @Command(name = "credentials", description = "Manage OAuth credentials", mixinStandardHelpOptions = true)
    static class CredentialsCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Inject
        ObjectMapper mapper;

        @Parameters(index = "0", arity = "0..1", description = "Path to credentials.json file")
        File credentialsFile;

        @Option(names = "--name", description = "Credential name (default: 'default')")
        String name;

        @Option(names = "--list", description = "List all credentials")
        boolean list;

        @Option(names = "--remove", description = "Remove named credentials")
        String remove;

        @Override
        public Integer call() throws Exception {
            if (list) {
                return listCredentials();
            } else if (remove != null) {
                return removeCredentials();
            } else if (credentialsFile != null) {
                return saveCredentials();
            } else {
                // Show usage
                System.out.println("Usage:");
                System.out.println("  jgmcli accounts credentials <file.json> [--name <name>]");
                System.out.println("  jgmcli accounts credentials --list");
                System.out.println("  jgmcli accounts credentials --remove <name>");
                return 1;
            }
        }

        private Integer listCredentials() {
            CredentialsStore store = service.getAllCredentials();
            if (store.isEmpty()) {
                System.out.println("No credentials configured");
                return 0;
            }

            for (String credName : store.names()) {
                Credentials creds = store.get(credName);
                String clientIdPreview = creds.clientId();
                if (clientIdPreview.length() > 20) {
                    clientIdPreview = clientIdPreview.substring(0, 20) + "...";
                }
                System.out.println(credName + "\t" + clientIdPreview);
            }
            return 0;
        }

        private Integer removeCredentials() {
            if (service.removeCredentials(remove)) {
                System.out.println("Removed credentials '" + remove + "'");
                return 0;
            } else {
                System.out.println("Credentials '" + remove + "' not found");
                return 1;
            }
        }

        private Integer saveCredentials() throws Exception {
            Credentials.GoogleCredentialsFile googleFile = mapper.readValue(
                credentialsFile, Credentials.GoogleCredentialsFile.class);
            Credentials creds = Credentials.fromGoogleFormat(googleFile);

            if (creds == null || creds.clientId() == null || creds.clientSecret() == null) {
                // Try direct format
                creds = mapper.readValue(credentialsFile, Credentials.class);
            }

            if (creds == null || creds.clientId() == null || creds.clientSecret() == null) {
                System.err.println("Error: Invalid credentials file");
                return 1;
            }

            String credName = name != null ? name : Credentials.DEFAULT_NAME;
            service.setCredentials(credName, creds.clientId(), creds.clientSecret());
            
            if (name != null) {
                System.out.println("Credentials '" + name + "' saved");
            } else {
                System.out.println("Default credentials saved");
            }
            return 0;
        }
    }

    @Command(name = "add", description = "Add account", mixinStandardHelpOptions = true)
    static class AddCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email address")
        String email;

        @Option(names = "--manual", description = "Browserless OAuth flow")
        boolean manual;

        @Option(names = "--credentials", description = "Named credentials to use")
        String credentialsName;

        @Option(names = "--force", description = "Re-authorize existing account")
        boolean force;

        @Override
        public Integer call() {
            service.addAccount(email, credentialsName, manual, force);
            
            if (credentialsName != null) {
                System.out.println("Account '" + email + "' added (using '" + credentialsName + "' credentials)");
            } else {
                System.out.println("Account '" + email + "' added");
            }
            return 0;
        }
    }

    @Command(name = "remove", description = "Remove account", mixinStandardHelpOptions = true)
    static class RemoveCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email address")
        String email;

        @Override
        public Integer call() {
            boolean deleted = service.deleteAccount(email);
            System.out.println(deleted ? "Removed '" + email + "'" : "Not found: " + email);
            return 0;
        }
    }
}
