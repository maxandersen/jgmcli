package dk.xam.jgmcli.cli;

import dk.xam.jgmcli.model.ThreadSearchResult;
import dk.xam.jgmcli.service.GmailService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static dk.xam.jgmcli.cli.OutputFormatter.*;

@Command(name = "search", description = "Search threads using Gmail query syntax", mixinStandardHelpOptions = true)
public class SearchCommand implements Callable<Integer> {

    @Inject
    GmailService service;

    @Parameters(index = "0", description = "Email account")
    String email;

    @Parameters(index = "1", description = "Gmail query (e.g., 'in:inbox is:unread')")
    String query;

    @Option(names = "--max", description = "Max results (default: 10)")
    Integer maxResults;

    @Option(names = "--page", description = "Page token for pagination")
    String pageToken;

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Override
    public Integer call() throws Exception {
        int max = maxResults != null ? maxResults : 10;
        ThreadSearchResult result = service.searchThreads(email, query, max, pageToken);
        
        if (result.threads().isEmpty()) {
            System.out.println("No results");
            return 0;
        }
        
        // Get label mapping
        Map<String, String> idToName = service.getLabelIdToNameMap(email);
        
        List<String[]> rows = new ArrayList<>();
        for (ThreadSearchResult.ThreadInfo thread : result.threads()) {
            if (thread.messages().isEmpty()) {
                continue;
            }
            ThreadSearchResult.MessageInfo msg = thread.messages().get(0);
            
            String date = "";
            if (msg.internalDate() != null && !msg.internalDate().isEmpty()) {
                try {
                    long timestamp = Long.parseLong(msg.internalDate());
                    date = DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp));
                } catch (NumberFormatException e) {
                    date = msg.internalDate();
                }
            }
            
            String labels = msg.labelIds().stream()
                .map(id -> idToName.getOrDefault(id, id))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            
            rows.add(new String[]{
                thread.id(),
                date,
                sanitize(msg.from()),
                sanitize(orDefault(msg.subject(), "(no subject)")),
                labels
            });
        }
        
        printTable(new String[]{"ID", "DATE", "FROM", "SUBJECT", "LABELS"}, rows);
        
        if (result.nextPageToken() != null) {
            System.out.println();
            System.out.println("# Next page: --page " + result.nextPageToken());
        }
        
        return 0;
    }
}
