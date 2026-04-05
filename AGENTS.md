# Agent Guide for jgmcli

## Project Overview

Java port of [gmcli](https://github.com/badlogic/pi-skills/tree/main/gmcli) (TypeScript). Keep feature parity with the original.

**Source:** https://github.com/maxandersen/jgmcli

## Architecture

- **Framework:** Quarkus 3.x + Picocli (`quarkus-picocli` extension)
- **Java version:** 21+
- **Entry point:** `@TopCommand` on `GmcliCommand.java`
- **DI:** `@ApplicationScoped` services, `@Inject` in commands
- **Google API:** `google-api-services-gmail` + `google-oauth-client-jetty`

## Package Structure

```
dk.xam.jgmcli/
├── cli/                 # Command classes (one per subcommand)
│   ├── GmcliCommand.java      # @TopCommand - main entry
│   ├── AccountsCommand.java   # accounts subcommand
│   ├── SearchCommand.java     # search subcommand
│   ├── ThreadCommand.java     # thread subcommand
│   └── ...
├── service/             # Business logic & Google API calls
│   └── GmailService.java
├── storage/             # Persistence
│   └── AccountStorage.java
├── oauth/               # OAuth flow handling
│   └── GmailOAuthFlow.java
├── model/               # Records/POJOs
│   ├── GmailAccount.java
│   ├── ThreadSearchResult.java
│   └── ...
└── exception/           # Custom exceptions
```

## Key Patterns

- **Commands:** Each subcommand is a separate class with `@Command` annotation
- **Output:** `OutputFormatter` handles `--json` flag (JSON vs TSV output)
- **Storage:** `AccountStorage` manages OAuth tokens in `~/.jgcli/` (shared)
- **Credentials:** Support multiple named credential sets

## Adding a New Command

1. Create `XxxCommand.java` in `cli/` package
2. Add `@Command(name = "xxx", description = "...")` annotation
3. Implement `Callable<Integer>` returning exit code
4. Register in `GmcliCommand.java` `subcommands` array
5. Inject `GmailService` for API calls

## Common Pitfalls

### Thread Class Ambiguity
**Always use fully qualified name for Gmail Thread:**
```java
// WRONG - ambiguous with java.lang.Thread
Thread thread = service.getThread(id);

// CORRECT
com.google.api.services.gmail.model.Thread thread = service.getThread(id);
```

### Message Content
- Messages are base64url encoded - use `Base64.getUrlDecoder()`
- MIME parsing needed for multipart messages
- HTML body often in `text/html` part, plain text in `text/plain`

### API Errors
- Handle `GoogleJsonResponseException` for meaningful error messages
- 404 = thread/message not found
- 400 = invalid query syntax

## Build & Test

```bash
# Quick test with JBang (no build needed)
jbang src/main/java/jgmcli.java --help
jbang src/main/java/jgmcli.java accounts list

# Full Maven build
mvn package
./jgmcli --help
```

## Config & Data

- `~/.jgcli/credentials.json` - OAuth client credentials (shared with jgccli, jgdcli)
- `~/.jgcli/credentials-<name>.json` - Named credential sets (shared)
- `~/.jgcli/accounts-gmail.json` - Gmail account tokens
- `~/.jgcli/attachments/` - Downloaded attachments

## Gmail Query Syntax

```
in:inbox, in:sent, in:drafts, in:trash
is:unread, is:starred, is:important
from:sender@example.com
to:recipient@example.com
subject:keyword
has:attachment
filename:pdf
after:2024/01/01, before:2024/12/31
label:Work
```

Combine with spaces: `in:inbox is:unread from:boss@company.com`

## Related Projects

- **jgccli** - Google Calendar CLI (same patterns): https://github.com/maxandersen/jgccli
- **jgdcli** - Google Drive CLI (same patterns): https://github.com/maxandersen/jgdcli
- **Original TypeScript:** https://github.com/badlogic/pi-skills/tree/main/gmcli
