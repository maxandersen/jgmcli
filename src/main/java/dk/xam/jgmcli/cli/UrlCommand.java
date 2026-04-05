package dk.xam.jgmcli.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "url", description = "Generate Gmail web URLs for threads", mixinStandardHelpOptions = true)
public class UrlCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Email account")
    String email;

    @Parameters(index = "1..*", arity = "1..*", description = "Thread IDs")
    List<String> threadIds;

    @Override
    public Integer call() {
        for (String threadId : threadIds) {
            String url = "https://mail.google.com/mail/?authuser=" + 
                URLEncoder.encode(email, StandardCharsets.UTF_8) + "#all/" + threadId;
            System.out.println(threadId + "\t" + url);
        }
        return 0;
    }
}
