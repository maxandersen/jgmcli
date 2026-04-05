package dk.xam.jgmcli.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListDraftsResponse;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListThreadsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyThreadRequest;
import dk.xam.jgmcli.exception.AccountNotFoundException;
import dk.xam.jgmcli.exception.GmcliException;
import dk.xam.jgmcli.model.*;
import dk.xam.jgmcli.model.Credentials.CredentialsStore;
import dk.xam.jgmcli.oauth.GmailOAuthFlow;
import dk.xam.jgmcli.storage.AccountStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GmailService {

    private static final String APPLICATION_NAME = "jgmcli";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Inject
    AccountStorage accountStorage;

    private final Map<String, Gmail> gmailClients = new ConcurrentHashMap<>();
    private NetHttpTransport transport;

    private NetHttpTransport getTransport() {
        if (transport == null) {
            try {
                transport = GoogleNetHttpTransport.newTrustedTransport();
            } catch (GeneralSecurityException | IOException e) {
                throw new GmcliException("Failed to initialize HTTP transport", e);
            }
        }
        return transport;
    }

    // Account management

    public void addAccount(String email, String credentialsName, boolean manual, boolean force) {
        if (accountStorage.hasAccount(email) && !force) {
            throw new GmcliException("Account '" + email + "' already exists. Use --force to re-authorize.");
        }

        Credentials creds = accountStorage.getCredentials(credentialsName);
        if (creds == null) {
            String name = credentialsName != null ? credentialsName : Credentials.DEFAULT_NAME;
            throw new GmcliException("Credentials '" + name + "' not found. Run: jgmcli accounts credentials <file.json>" +
                (credentialsName != null ? " --name " + credentialsName : ""));
        }

        GmailOAuthFlow oauthFlow = new GmailOAuthFlow(creds.clientId(), creds.clientSecret());
        String refreshToken = oauthFlow.authorize(manual);

        GmailAccount account = new GmailAccount(
            email,
            credentialsName,
            new OAuth2Credentials(creds.clientId(), creds.clientSecret(), refreshToken)
        );

        gmailClients.remove(email);
        accountStorage.addAccount(account);
    }

    public boolean deleteAccount(String email) {
        gmailClients.remove(email);
        return accountStorage.deleteAccount(email);
    }

    public List<GmailAccount> listAccounts() {
        return accountStorage.getAllAccounts();
    }

    public void setCredentials(String name, String clientId, String clientSecret) {
        accountStorage.setCredentials(name, clientId, clientSecret);
    }

    public Credentials getCredentials(String name) {
        return accountStorage.getCredentials(name);
    }

    public CredentialsStore getAllCredentials() {
        return accountStorage.getAllCredentials();
    }

    public boolean removeCredentials(String name) {
        return accountStorage.removeCredentials(name);
    }

    @SuppressWarnings("deprecation")
    private Gmail getGmailClient(String email) {
        return gmailClients.computeIfAbsent(email, e -> {
            GmailAccount account = accountStorage.getAccount(e);
            if (account == null) {
                throw new AccountNotFoundException(e);
            }

            GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(getTransport())
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(account.oauth2().clientId(), account.oauth2().clientSecret())
                .build()
                .setRefreshToken(account.oauth2().refreshToken());

            if (account.oauth2().accessToken() != null) {
                credential.setAccessToken(account.oauth2().accessToken());
            }

            return new Gmail.Builder(getTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        });
    }

    // Thread operations

    public ThreadSearchResult searchThreads(String email, String query, int maxResults, String pageToken) throws IOException {
        Gmail gmail = getGmailClient(email);
        
        Gmail.Users.Threads.List request = gmail.users().threads().list("me")
            .setQ(query)
            .setMaxResults((long) maxResults);
        
        if (pageToken != null) {
            request.setPageToken(pageToken);
        }
        
        ListThreadsResponse response = request.execute();
        List<com.google.api.services.gmail.model.Thread> threads = response.getThreads();
        
        if (threads == null || threads.isEmpty()) {
            return new ThreadSearchResult(Collections.emptyList(), null);
        }
        
        List<ThreadSearchResult.ThreadInfo> threadInfos = new ArrayList<>();
        for (com.google.api.services.gmail.model.Thread thread : threads) {
            com.google.api.services.gmail.model.Thread fullThread = gmail.users().threads().get("me", thread.getId()).execute();
            threadInfos.add(convertThread(fullThread));
        }
        
        return new ThreadSearchResult(threadInfos, response.getNextPageToken());
    }

    private ThreadSearchResult.ThreadInfo convertThread(com.google.api.services.gmail.model.Thread thread) {
        List<ThreadSearchResult.MessageInfo> messages = new ArrayList<>();
        if (thread.getMessages() != null) {
            for (Message msg : thread.getMessages()) {
                messages.add(convertMessage(msg));
            }
        }
        return new ThreadSearchResult.ThreadInfo(
            thread.getId(),
            thread.getHistoryId() != null ? thread.getHistoryId().toString() : "",
            messages
        );
    }

    private ThreadSearchResult.MessageInfo convertMessage(Message msg) {
        return new ThreadSearchResult.MessageInfo(
            msg.getId(),
            msg.getThreadId(),
            msg.getLabelIds() != null ? msg.getLabelIds() : Collections.emptyList(),
            msg.getSnippet() != null ? msg.getSnippet() : "",
            msg.getHistoryId() != null ? msg.getHistoryId().toString() : "",
            msg.getInternalDate() != null ? msg.getInternalDate().toString() : "",
            getHeaderValue(msg, "from"),
            getHeaderValue(msg, "to"),
            getHeaderValue(msg, "subject"),
            getHeaderValue(msg, "date"),
            hasAttachments(msg.getPayload())
        );
    }

    private String getHeaderValue(Message msg, String headerName) {
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) {
            return "";
        }
        return msg.getPayload().getHeaders().stream()
            .filter(h -> headerName.equalsIgnoreCase(h.getName()))
            .map(MessagePartHeader::getValue)
            .findFirst()
            .orElse("");
    }

    private boolean hasAttachments(MessagePart payload) {
        if (payload == null || payload.getParts() == null) {
            return false;
        }
        return payload.getParts().stream()
            .anyMatch(part -> part.getFilename() != null && !part.getFilename().isEmpty());
    }

    public com.google.api.services.gmail.model.Thread getThread(String email, String threadId) throws IOException {
        Gmail gmail = getGmailClient(email);
        return gmail.users().threads().get("me", threadId).execute();
    }

    // Label operations

    public List<LabelInfo> listLabels(String email) throws IOException {
        Gmail gmail = getGmailClient(email);
        ListLabelsResponse response = gmail.users().labels().list("me").execute();
        List<Label> labels = response.getLabels();
        
        if (labels == null) {
            return Collections.emptyList();
        }
        
        return labels.stream()
            .map(l -> new LabelInfo(
                l.getId() != null ? l.getId() : "",
                l.getName() != null ? l.getName() : "",
                l.getType() != null ? l.getType() : ""
            ))
            .toList();
    }

    public Map<String, String> getLabelIdToNameMap(String email) throws IOException {
        Map<String, String> idToName = new HashMap<>();
        for (LabelInfo label : listLabels(email)) {
            idToName.put(label.id(), label.name());
        }
        return idToName;
    }

    public Map<String, String> getLabelNameToIdMap(String email) throws IOException {
        Map<String, String> nameToId = new HashMap<>();
        for (LabelInfo label : listLabels(email)) {
            nameToId.put(label.name().toLowerCase(), label.id());
        }
        return nameToId;
    }

    public List<String> resolveLabelIds(List<String> labels, Map<String, String> nameToId) {
        return labels.stream()
            .map(l -> nameToId.getOrDefault(l.toLowerCase(), l))
            .toList();
    }

    public List<LabelOperationResult> modifyLabels(String email, List<String> threadIds, 
                                                    List<String> addLabels, List<String> removeLabels) throws IOException {
        Gmail gmail = getGmailClient(email);
        List<LabelOperationResult> results = new ArrayList<>();
        
        for (String threadId : threadIds) {
            try {
                ModifyThreadRequest request = new ModifyThreadRequest();
                if (addLabels != null && !addLabels.isEmpty()) {
                    request.setAddLabelIds(addLabels);
                }
                if (removeLabels != null && !removeLabels.isEmpty()) {
                    request.setRemoveLabelIds(removeLabels);
                }
                gmail.users().threads().modify("me", threadId, request).execute();
                results.add(new LabelOperationResult(threadId, true));
            } catch (Exception e) {
                results.add(new LabelOperationResult(threadId, false, e.getMessage()));
            }
        }
        
        return results;
    }

    // Draft operations

    public List<Draft> listDrafts(String email) throws IOException {
        Gmail gmail = getGmailClient(email);
        ListDraftsResponse response = gmail.users().drafts().list("me").execute();
        return response.getDrafts() != null ? response.getDrafts() : Collections.emptyList();
    }

    public Draft getDraft(String email, String draftId) throws IOException {
        Gmail gmail = getGmailClient(email);
        return gmail.users().drafts().get("me", draftId).execute();
    }

    public void deleteDraft(String email, String draftId) throws IOException {
        Gmail gmail = getGmailClient(email);
        gmail.users().drafts().delete("me", draftId).execute();
    }

    public Message sendDraft(String email, String draftId) throws IOException {
        Gmail gmail = getGmailClient(email);
        Draft draft = new Draft().setId(draftId);
        return gmail.users().drafts().send("me", draft).execute();
    }

    public Draft createDraft(String email, List<String> to, String subject, String body,
                             List<String> cc, List<String> bcc, String replyToMessageId,
                             List<String> attachments) throws IOException {
        Gmail gmail = getGmailClient(email);
        
        String threadId = null;
        String inReplyTo = null;
        String references = null;
        
        // Get reply headers if replying
        if (replyToMessageId != null) {
            Message replyMsg = gmail.users().messages().get("me", replyToMessageId)
                .setFormat("metadata")
                .setMetadataHeaders(List.of("Message-ID", "References"))
                .execute();
            
            threadId = replyMsg.getThreadId();
            
            if (replyMsg.getPayload() != null && replyMsg.getPayload().getHeaders() != null) {
                for (MessagePartHeader header : replyMsg.getPayload().getHeaders()) {
                    if ("Message-ID".equalsIgnoreCase(header.getName())) {
                        inReplyTo = header.getValue();
                    } else if ("References".equalsIgnoreCase(header.getName())) {
                        references = header.getValue();
                    }
                }
            }
            
            if (inReplyTo != null) {
                references = references != null ? references + " " + inReplyTo : inReplyTo;
            }
        }
        
        String rawEmail = buildRawEmail(email, to, subject, body, cc, bcc, inReplyTo, references, attachments);
        
        Message message = new Message().setRaw(rawEmail);
        if (threadId != null) {
            message.setThreadId(threadId);
        }
        
        Draft draft = new Draft().setMessage(message);
        return gmail.users().drafts().create("me", draft).execute();
    }

    // Send operations

    public Message sendMessage(String email, List<String> to, String subject, String body,
                               List<String> cc, List<String> bcc, String replyToMessageId,
                               List<String> attachments) throws IOException {
        Gmail gmail = getGmailClient(email);
        
        String threadId = null;
        String inReplyTo = null;
        String references = null;
        
        // Get reply headers if replying
        if (replyToMessageId != null) {
            Message replyMsg = gmail.users().messages().get("me", replyToMessageId)
                .setFormat("metadata")
                .setMetadataHeaders(List.of("Message-ID", "References"))
                .execute();
            
            threadId = replyMsg.getThreadId();
            
            if (replyMsg.getPayload() != null && replyMsg.getPayload().getHeaders() != null) {
                for (MessagePartHeader header : replyMsg.getPayload().getHeaders()) {
                    if ("Message-ID".equalsIgnoreCase(header.getName())) {
                        inReplyTo = header.getValue();
                    } else if ("References".equalsIgnoreCase(header.getName())) {
                        references = header.getValue();
                    }
                }
            }
            
            if (inReplyTo != null) {
                references = references != null ? references + " " + inReplyTo : inReplyTo;
            }
        }
        
        String rawEmail = buildRawEmail(email, to, subject, body, cc, bcc, inReplyTo, references, attachments);
        
        Message message = new Message().setRaw(rawEmail);
        if (threadId != null) {
            message.setThreadId(threadId);
        }
        
        return gmail.users().messages().send("me", message).execute();
    }

    private String buildRawEmail(String from, List<String> to, String subject, String body,
                                  List<String> cc, List<String> bcc,
                                  String inReplyTo, String references,
                                  List<String> attachments) throws IOException {
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        String boundary = "boundary_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        StringBuilder headers = new StringBuilder();
        headers.append("From: ").append(from).append("\r\n");
        headers.append("To: ").append(String.join(", ", to)).append("\r\n");
        if (cc != null && !cc.isEmpty()) {
            headers.append("Cc: ").append(String.join(", ", cc)).append("\r\n");
        }
        if (bcc != null && !bcc.isEmpty()) {
            headers.append("Bcc: ").append(String.join(", ", bcc)).append("\r\n");
        }
        headers.append("Subject: ").append(subject).append("\r\n");
        if (inReplyTo != null) {
            headers.append("In-Reply-To: ").append(inReplyTo).append("\r\n");
        }
        if (references != null) {
            headers.append("References: ").append(references).append("\r\n");
        }
        headers.append("MIME-Version: 1.0\r\n");
        
        StringBuilder email = new StringBuilder();
        
        if (hasAttachments) {
            headers.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n");
            email.append(headers).append("\r\n");
            
            // Text body part
            email.append("--").append(boundary).append("\r\n");
            email.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
            email.append(body).append("\r\n");
            
            // Attachment parts
            for (String filePath : attachments) {
                Path path = Path.of(filePath);
                String filename = path.getFileName().toString();
                byte[] fileContent = Files.readAllBytes(path);
                String base64Content = Base64.getEncoder().encodeToString(fileContent);
                String mimeType = getMimeType(filename);
                
                email.append("--").append(boundary).append("\r\n");
                email.append("Content-Type: ").append(mimeType).append("\r\n");
                email.append("Content-Transfer-Encoding: base64\r\n");
                email.append("Content-Disposition: attachment; filename=\"").append(filename).append("\"\r\n\r\n");
                email.append(base64Content).append("\r\n");
            }
            
            email.append("--").append(boundary).append("--\r\n");
        } else {
            headers.append("Content-Type: text/plain; charset=UTF-8\r\n");
            email.append(headers).append("\r\n").append(body);
        }
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(email.toString().getBytes());
    }

    private String getMimeType(String filename) {
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".txt" -> "text/plain";
            case ".html" -> "text/html";
            case ".zip" -> "application/zip";
            case ".json" -> "application/json";
            default -> "application/octet-stream";
        };
    }

    // Attachment operations

    public List<DownloadedAttachment> downloadThreadAttachments(String email, String threadId) throws IOException {
        Gmail gmail = getGmailClient(email);
        com.google.api.services.gmail.model.Thread thread = gmail.users().threads().get("me", threadId).execute();
        
        List<DownloadedAttachment> downloaded = new ArrayList<>();
        if (thread.getMessages() == null) {
            return downloaded;
        }
        
        Path attachmentsDir = accountStorage.getAttachmentsDir();
        
        for (Message msg : thread.getMessages()) {
            downloaded.addAll(downloadMessageAttachments(email, msg, attachmentsDir));
        }
        
        return downloaded;
    }

    public List<DownloadedAttachment> downloadMessageAttachments(String email, String messageId) throws IOException {
        Gmail gmail = getGmailClient(email);
        Message msg = gmail.users().messages().get("me", messageId).execute();
        Path attachmentsDir = accountStorage.getAttachmentsDir();
        return downloadMessageAttachments(email, msg, attachmentsDir);
    }

    private List<DownloadedAttachment> downloadMessageAttachments(String email, Message msg, Path attachmentsDir) throws IOException {
        Gmail gmail = getGmailClient(email);
        List<DownloadedAttachment> downloaded = new ArrayList<>();
        
        if (msg.getPayload() == null) {
            return downloaded;
        }
        
        collectAndDownloadAttachments(gmail, email, msg.getId(), msg.getPayload(), attachmentsDir, downloaded);
        return downloaded;
    }

    private void collectAndDownloadAttachments(Gmail gmail, String email, String messageId, 
                                                MessagePart payload, Path attachmentsDir,
                                                List<DownloadedAttachment> downloaded) throws IOException {
        if (payload == null) {
            return;
        }
        
        if (payload.getFilename() != null && !payload.getFilename().isEmpty() && 
            payload.getBody() != null && payload.getBody().getAttachmentId() != null) {
            
            String attachmentId = payload.getBody().getAttachmentId();
            String filename = payload.getFilename();
            long size = payload.getBody().getSize() != null ? payload.getBody().getSize() : 0;
            String mimeType = payload.getMimeType() != null ? payload.getMimeType() : "application/octet-stream";
            
            String shortAttachmentId = attachmentId.substring(0, Math.min(8, attachmentId.length()));
            String savedFilename = messageId + "_" + shortAttachmentId + "_" + filename;
            Path filePath = attachmentsDir.resolve(savedFilename);
            
            boolean cached = false;
            if (Files.exists(filePath)) {
                long existingSize = Files.size(filePath);
                if (existingSize == size) {
                    cached = true;
                } else {
                    // Redownload
                    downloadAttachment(gmail, messageId, attachmentId, filePath);
                }
            } else {
                downloadAttachment(gmail, messageId, attachmentId, filePath);
            }
            
            downloaded.add(new DownloadedAttachment(messageId, filename, filePath.toString(), size, mimeType, cached));
        }
        
        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                collectAndDownloadAttachments(gmail, email, messageId, part, attachmentsDir, downloaded);
            }
        }
    }

    private void downloadAttachment(Gmail gmail, String messageId, String attachmentId, Path filePath) throws IOException {
        MessagePartBody attachment = gmail.users().messages().attachments()
            .get("me", messageId, attachmentId)
            .execute();
        
        byte[] data = Base64.getUrlDecoder().decode(attachment.getData());
        Files.write(filePath, data);
    }

    // Body decoding helpers

    public String decodeMessageBody(Message msg) {
        if (msg.getPayload() == null) {
            return "";
        }
        return decodePayloadBody(msg.getPayload());
    }

    private String decodePayloadBody(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        
        // Direct body data
        if (payload.getBody() != null && payload.getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(payload.getBody().getData()));
        }
        
        // Look for text/plain in parts
        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if ("text/plain".equals(part.getMimeType()) && 
                    part.getBody() != null && part.getBody().getData() != null) {
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                }
            }
            // Recursively search
            for (MessagePart part : payload.getParts()) {
                String nested = decodePayloadBody(part);
                if (!nested.isEmpty()) {
                    return nested;
                }
            }
        }
        
        return "";
    }

    // Attachment info helpers

    public List<AttachmentInfo> getAttachmentInfos(MessagePart payload) {
        List<AttachmentInfo> attachments = new ArrayList<>();
        collectAttachmentInfos(payload, attachments);
        return attachments;
    }

    private void collectAttachmentInfos(MessagePart payload, List<AttachmentInfo> attachments) {
        if (payload == null) {
            return;
        }
        
        if (payload.getFilename() != null && !payload.getFilename().isEmpty() &&
            payload.getBody() != null && payload.getBody().getAttachmentId() != null) {
            attachments.add(new AttachmentInfo(
                payload.getFilename(),
                payload.getBody().getSize() != null ? payload.getBody().getSize() : 0,
                payload.getMimeType() != null ? payload.getMimeType() : "application/octet-stream"
            ));
        }
        
        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                collectAttachmentInfos(part, attachments);
            }
        }
    }

    public record AttachmentInfo(String filename, long size, String mimeType) {}
}
