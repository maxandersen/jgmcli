package dk.xam.jgmcli.cli;

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
import java.util.List;
import java.util.concurrent.Callable;

import static dk.xam.jgmcli.cli.OutputFormatter.*;

@Command(name = "thread", description = "Get thread with all messages", mixinStandardHelpOptions = true)
public class ThreadCommand implements Callable<Integer> {

    @Inject
    GmailService service;

    @Parameters(index = "0", description = "Email account")
    String email;

    @Parameters(index = "1", description = "Thread ID")
    String threadId;

    @Option(names = "--download", description = "Download attachments to ~/.jgmcli/attachments/")
    boolean download;

    @Override
    public Integer call() throws Exception {
        if (download) {
            return downloadAttachments();
        } else {
            return showThread();
        }
    }

    private Integer showThread() throws Exception {
        com.google.api.services.gmail.model.Thread thread = service.getThread(email, threadId);
        
        if (thread.getMessages() == null || thread.getMessages().isEmpty()) {
            System.out.println("No messages in thread");
            return 0;
        }
        
        for (Message msg : thread.getMessages()) {
            printKeyValue("Message-ID", msg.getId());
            printKeyValue("From", getHeader(msg, "from"));
            printKeyValue("To", getHeader(msg, "to"));
            printKeyValue("Date", getHeader(msg, "date"));
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
                System.out.println();
            }
            
            System.out.println("---");
        }
        
        return 0;
    }

    private Integer downloadAttachments() throws Exception {
        List<DownloadedAttachment> downloaded = service.downloadThreadAttachments(email, threadId);
        
        if (downloaded.isEmpty()) {
            System.out.println("No attachments");
            return 0;
        }
        
        List<String[]> rows = new ArrayList<>();
        for (DownloadedAttachment att : downloaded) {
            rows.add(new String[]{att.filename(), att.path(), String.valueOf(att.size())});
        }
        
        printTable(new String[]{"FILENAME", "PATH", "SIZE"}, rows);
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
