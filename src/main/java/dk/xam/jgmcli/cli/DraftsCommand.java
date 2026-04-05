package dk.xam.jgmcli.cli;

import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import dk.xam.jgmcli.model.DownloadedAttachment;
import dk.xam.jgmcli.service.GmailService;
import dk.xam.jgmcli.service.GmailService.AttachmentInfo;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static dk.xam.jgmcli.cli.OutputFormatter.*;

@Command(name = "drafts", description = "Draft operations",
         mixinStandardHelpOptions = true,
         subcommands = {
             DraftsCommand.ListDraftsCommand.class,
             DraftsCommand.GetDraftCommand.class,
             DraftsCommand.DeleteDraftCommand.class,
             DraftsCommand.SendDraftCommand.class,
             DraftsCommand.CreateDraftCommand.class
         })
public class DraftsCommand {

    @Command(name = "list", description = "List all drafts", mixinStandardHelpOptions = true)
    static class ListDraftsCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email account")
        String email;

        @Override
        public Integer call() throws Exception {
            List<Draft> drafts = service.listDrafts(email);
            
            if (drafts.isEmpty()) {
                System.out.println("No drafts");
                return 0;
            }
            
            List<String[]> rows = new ArrayList<>();
            for (Draft draft : drafts) {
                String messageId = draft.getMessage() != null ? orEmpty(draft.getMessage().getId()) : "";
                rows.add(new String[]{draft.getId(), messageId});
            }
            
            printTable(new String[]{"ID", "MESSAGE_ID"}, rows);
            return 0;
        }
    }

    @Command(name = "get", description = "View draft", mixinStandardHelpOptions = true)
    static class GetDraftCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email account")
        String email;

        @Parameters(index = "1", description = "Draft ID")
        String draftId;

        @Option(names = "--download", description = "Download attachments")
        boolean download;

        @Override
        public Integer call() throws Exception {
            Draft draft = service.getDraft(email, draftId);
            Message msg = draft.getMessage();
            
            if (msg == null) {
                System.out.println("Draft has no message content");
                return 0;
            }
            
            if (download) {
                List<DownloadedAttachment> downloaded = service.downloadMessageAttachments(email, msg.getId());
                if (downloaded.isEmpty()) {
                    System.out.println("No attachments");
                } else {
                    List<String[]> rows = new ArrayList<>();
                    for (DownloadedAttachment att : downloaded) {
                        rows.add(new String[]{att.filename(), att.path(), String.valueOf(att.size())});
                    }
                    printTable(new String[]{"FILENAME", "PATH", "SIZE"}, rows);
                }
            } else {
                printKeyValue("Draft-ID", draft.getId());
                printKeyValue("To", getHeader(msg, "to"));
                printKeyValue("Cc", getHeader(msg, "cc"));
                printKeyValue("Subject", getHeader(msg, "subject"));
                System.out.println();
                
                String body = service.decodeMessageBody(msg);
                System.out.println(body);
                System.out.println();
                
                List<AttachmentInfo> attachments = service.getAttachmentInfos(msg.getPayload());
                if (!attachments.isEmpty()) {
                    System.out.println("Attachments:");
                    for (AttachmentInfo att : attachments) {
                        System.out.println("  - " + att.filename() + " (" + formatSize(att.size()) + ", " + att.mimeType() + ")");
                    }
                }
            }
            
            return 0;
        }

        private String getHeader(Message msg, String name) {
            if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) {
                return "";
            }
            return msg.getPayload().getHeaders().stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
        }
    }

    @Command(name = "delete", description = "Delete a draft", mixinStandardHelpOptions = true)
    static class DeleteDraftCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email account")
        String email;

        @Parameters(index = "1", description = "Draft ID")
        String draftId;

        @Override
        public Integer call() throws Exception {
            service.deleteDraft(email, draftId);
            System.out.println("Deleted");
            return 0;
        }
    }

    @Command(name = "send", description = "Send a draft", mixinStandardHelpOptions = true)
    static class SendDraftCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email account")
        String email;

        @Parameters(index = "1", description = "Draft ID")
        String draftId;

        @Override
        public Integer call() throws Exception {
            Message msg = service.sendDraft(email, draftId);
            System.out.println("Sent: " + msg.getId());
            return 0;
        }
    }

    @Command(name = "create", description = "Create a new draft", mixinStandardHelpOptions = true)
    static class CreateDraftCommand implements Callable<Integer> {
        @Inject
        GmailService service;

        @Parameters(index = "0", description = "Email account")
        String email;

        @Option(names = "--to", required = true, description = "Recipients (comma-separated)")
        String to;

        @Option(names = "--subject", required = true, description = "Subject line")
        String subject;

        @Option(names = "--body", required = true, description = "Message body")
        String body;

        @Option(names = "--cc", description = "CC recipients (comma-separated)")
        String cc;

        @Option(names = "--bcc", description = "BCC recipients (comma-separated)")
        String bcc;

        @Option(names = "--reply-to", description = "Reply to message ID (sets headers and thread)")
        String replyTo;

        @Option(names = "--attach", description = "Attach file (use multiple times for multiple files)")
        List<String> attachments;

        @Override
        public Integer call() throws Exception {
            Draft draft = service.createDraft(
                email,
                Arrays.asList(to.split(",")),
                subject,
                body,
                cc != null ? Arrays.asList(cc.split(",")) : null,
                bcc != null ? Arrays.asList(bcc.split(",")) : null,
                replyTo,
                attachments
            );
            
            System.out.println("Draft created: " + draft.getId());
            return 0;
        }
    }
}
