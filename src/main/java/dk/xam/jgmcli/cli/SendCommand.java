package dk.xam.jgmcli.cli;

import com.google.api.services.gmail.model.Message;
import dk.xam.jgmcli.service.GmailService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "send", description = "Send an email directly", mixinStandardHelpOptions = true)
public class SendCommand implements Callable<Integer> {

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
        Message msg = service.sendMessage(
            email,
            Arrays.asList(to.split(",")),
            subject,
            body,
            cc != null ? Arrays.asList(cc.split(",")) : null,
            bcc != null ? Arrays.asList(bcc.split(",")) : null,
            replyTo,
            attachments
        );
        
        System.out.println("Sent: " + msg.getId());
        return 0;
    }
}
