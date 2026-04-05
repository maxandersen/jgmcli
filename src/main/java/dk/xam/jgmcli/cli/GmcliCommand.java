package dk.xam.jgmcli.cli;

import dk.xam.jgmcli.exception.GmcliException;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

@TopCommand
@Command(name = "jgmcli",
         mixinStandardHelpOptions = true,
         version = "jgmcli 0.1.0",
         description = "Minimal Google Gmail CLI (Java)",
         subcommands = {
             AccountsCommand.class,
             SearchCommand.class,
             ThreadCommand.class,
             LabelsCommand.class,
             DraftsCommand.class,
             SendCommand.class,
             UrlCommand.class
         },
         footer = {
             "",
             "USAGE EXAMPLES",
             "",
             "  Credentials management:",
             "    jgmcli accounts credentials ~/creds.json              # Set default",
             "    jgmcli accounts credentials ~/work.json --name work   # Set named",
             "    jgmcli accounts credentials --list                    # List all",
             "    jgmcli accounts credentials --remove work             # Remove",
             "",
             "  Account management:",
             "    jgmcli accounts add you@gmail.com                     # Use default creds",
             "    jgmcli accounts add you@work.com --credentials work   # Use named creds",
             "    jgmcli accounts add you@gmail.com --manual            # Browserless",
             "    jgmcli accounts add you@gmail.com --force             # Re-authorize",
             "    jgmcli accounts list",
             "    jgmcli accounts remove you@gmail.com",
             "",
             "  Gmail operations:",
             "    jgmcli search you@gmail.com \"in:inbox is:unread\"",
             "    jgmcli search you@gmail.com \"from:boss@company.com\" --max 50",
             "    jgmcli thread you@gmail.com 19aea1f2f3532db5",
             "    jgmcli thread you@gmail.com 19aea1f2f3532db5 --download",
             "    jgmcli labels you@gmail.com list",
             "    jgmcli labels you@gmail.com abc123 --add Work --remove UNREAD",
             "    jgmcli drafts you@gmail.com list",
             "    jgmcli drafts you@gmail.com create --to a@x.com --subject \"Hi\" --body \"Hello\"",
             "    jgmcli drafts you@gmail.com send r1234567890",
             "    jgmcli send you@gmail.com --to a@x.com --subject \"Hi\" --body \"Hello\"",
             "    jgmcli send you@gmail.com --to a@x.com --subject \"Re: Topic\" \\",
             "        --body \"Reply text\" --reply-to 19aea1f2f3532db5 --attach file.pdf",
             "    jgmcli url you@gmail.com 19aea1f2f3532db5 19aea1f2f3532db6",
             "",
             "  Query syntax examples:",
             "    in:inbox, in:sent, in:drafts, in:trash",
             "    is:unread, is:starred, is:important",
             "    from:sender@example.com, to:recipient@example.com",
             "    subject:keyword, has:attachment, filename:pdf",
             "    after:2024/01/01, before:2024/12/31",
             "    label:Work, label:UNREAD",
             "    Combine: \"in:inbox is:unread from:boss@company.com\"",
             "",
             "DATA STORAGE",
             "",
             "  ~/.jgmcli/credentials.json   OAuth client credentials",
             "  ~/.jgmcli/accounts.json      Account tokens",
             "  ~/.jgmcli/attachments/       Downloaded attachments"
         })
public class GmcliCommand implements IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
        String message = ex.getMessage();
        if (ex instanceof GmcliException) {
            cmd.getErr().println("Error: " + message);
        } else {
            cmd.getErr().println("Error: " + (message != null ? message : ex.getClass().getSimpleName()));
        }
        return 1;
    }
}
