package dk.xam.jgmcli.oauth;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import com.sun.net.httpserver.HttpServer;
import dk.xam.jgmcli.exception.AuthorizationException;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GmailOAuthFlow {

    // Full Gmail access scope
    private static final List<String> SCOPES = List.of(GmailScopes.MAIL_GOOGLE_COM);
    private static final int TIMEOUT_SECONDS = 120;
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final String clientId;
    private final String clientSecret;
    private final NetHttpTransport transport;

    public GmailOAuthFlow(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        try {
            this.transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize HTTP transport", e);
        }
    }

    public String authorize(boolean manual) {
        if (manual) {
            return startManualFlow();
        }
        return startBrowserFlow();
    }

    private String startBrowserFlow() {
        HttpServer server = null;
        try {
            // Start server on random port
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            int port = server.getAddress().getPort();
            String redirectUri = "http://localhost:" + port;

            GoogleAuthorizationCodeFlow flow = buildFlow(redirectUri);
            String authUrl = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                .build();

            CompletableFuture<String> codeFuture = new CompletableFuture<>();

            final HttpServer finalServer = server;
            server.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = null;
                String error = null;

                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2) {
                            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                            String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            if ("code".equals(key)) {
                                code = value;
                            } else if ("error".equals(key)) {
                                error = value;
                            }
                        }
                    }
                }

                String response;
                if (error != null) {
                    response = "<html><body><h1>Authorization cancelled</h1></body></html>";
                    codeFuture.completeExceptionally(new AuthorizationException("Authorization cancelled: " + error));
                } else if (code != null) {
                    response = "<html><body><h1>Success!</h1><p>You can close this window.</p></body></html>";
                    codeFuture.complete(code);
                } else {
                    response = "<html><body><h1>No authorization code</h1></body></html>";
                    codeFuture.completeExceptionally(new AuthorizationException("No authorization code received"));
                }

                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }

                // Stop server after handling
                new Thread(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    finalServer.stop(0);
                }).start();
            });

            server.start();

            System.out.println("Opening browser for Gmail authorization...");
            System.out.println("If browser doesn't open, visit this URL:");
            System.out.println(authUrl);

            openBrowser(authUrl);

            // Wait for the code with timeout
            String code = codeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Exchange code for tokens
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

            if (tokenResponse.getRefreshToken() == null) {
                throw new AuthorizationException("No refresh token received");
            }

            return tokenResponse.getRefreshToken();

        } catch (TimeoutException e) {
            throw new AuthorizationException("Authorization timed out after " + TIMEOUT_SECONDS + " seconds");
        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthorizationException("Authorization failed: " + e.getMessage(), e);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private String startManualFlow() {
        try {
            String redirectUri = "http://localhost:1";
            GoogleAuthorizationCodeFlow flow = buildFlow(redirectUri);

            String authUrl = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                .build();

            System.out.println("Visit this URL to authorize:");
            System.out.println(authUrl);
            System.out.println();
            System.out.println("After authorizing, you'll be redirected to a page that won't load.");
            System.out.println("Copy the URL from your browser's address bar and paste it here.");
            System.out.println();

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Paste redirect URL: ");
            String input = reader.readLine();

            if (input == null || input.isBlank()) {
                throw new AuthorizationException("No URL provided");
            }

            // Extract code from URL
            String code = null;
            URI uri = URI.create(input.trim());
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "code".equals(kv[0])) {
                        code = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        break;
                    }
                }
            }

            if (code == null) {
                throw new AuthorizationException("No authorization code found in URL");
            }

            // Exchange code for tokens
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

            if (tokenResponse.getRefreshToken() == null) {
                throw new AuthorizationException("No refresh token received");
            }

            return tokenResponse.getRefreshToken();

        } catch (AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthorizationException("Authorization failed: " + e.getMessage(), e);
        }
    }

    private GoogleAuthorizationCodeFlow buildFlow(String redirectUri) throws IOException {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
            .setClientId(clientId)
            .setClientSecret(clientSecret);
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        return new GoogleAuthorizationCodeFlow.Builder(transport, JSON_FACTORY, clientSecrets, SCOPES)
            .setAccessType("offline")
            .build();
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {}

        // Fallback to OS-specific commands
        String os = System.getProperty("os.name").toLowerCase();
        String[] command;
        if (os.contains("mac")) {
            command = new String[]{"open", url};
        } else if (os.contains("win")) {
            command = new String[]{"cmd", "/c", "start", url};
        } else {
            command = new String[]{"xdg-open", url};
        }

        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            // Browser opening is best-effort
        }
    }
}
