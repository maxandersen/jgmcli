# jgmcli - Minimal Google Gmail CLI (Java)

A minimal command-line interface for Google Gmail, written in Java using Quarkus and Picocli.

## Why Java?

This is a Java port of [gmcli](../gmcli/) (TypeScript/Node.js). Both implementations are functionally equivalent.

Use **jgmcli** if you prefer Java/JBang and don't want to install Node.js/npm.
Use **gmcli** if you prefer Node.js/TypeScript.

The command structure is nearly identical, making it easy to switch between them.

## Agent Skills

For AI coding agents (Claude Code, etc.), install the skill from [maxandersen/skills](https://github.com/maxandersen/skills):
```bash
npx skills install maxandersen/skills/jgmcli
```

## Features

- Multiple named OAuth credentials
- Browser + manual (browserless) OAuth flows
- Search threads using Gmail query syntax
- View threads with messages and attachments
- Download attachments
- List and modify labels
- Create, view, send, and delete drafts
- Send emails directly with attachments
- Generate Gmail web URLs
- Tab-separated output for easy parsing

## Run with JBang (no build required)

```bash
# Run directly from GitHub
jbang https://github.com/maxandersen/jgmcli/blob/main/src/main/java/jgmcli.java --help

# Install as 'jgmcli' command
jbang app install https://github.com/maxandersen/jgmcli/blob/main/src/main/java/jgmcli.java
jgmcli --help

# Or clone and run locally
jbang src/main/java/jgmcli.java --help
```

## Requirements

- Java 21+
- Maven 3.8+ (only if building)
- Google Cloud project with Gmail API enabled
- OAuth 2.0 credentials (Desktop app type)

## Building

```bash
mvn clean package -DskipTests
```

## Usage

### Initial Setup

1. Create OAuth credentials in Google Cloud Console:
   - Go to APIs & Services > Credentials
   - Create OAuth 2.0 Client ID (Desktop app)
   - Download the JSON file

2. Set credentials:
```bash
./jgmcli accounts credentials ~/path/to/credentials.json
```

3. Add an account:
```bash
./jgmcli accounts add you@gmail.com
```

### Shared Credentials

Credentials are stored in `~/.jgcli/` and **shared across jgccli, jgmcli, and jgdcli**. Set up once, use with all three tools:

```bash
# Set credentials once (from any tool)
jgmcli accounts credentials ~/path/to/credentials.json

# All tools can now use them
jgccli accounts add you@gmail.com   # Calendar
jgmcli accounts add you@gmail.com   # Gmail  
jgdcli accounts add you@gmail.com   # Drive
```

If you need separate credentials (e.g., different Google Cloud projects), use named credentials:

```bash
jgmcli accounts credentials ~/work-creds.json --name work
jgmcli accounts add work@company.com --credentials work
```

### Commands

```bash
# Account management
jgmcli accounts credentials ~/creds.json              # Set default credentials
jgmcli accounts credentials ~/work.json --name work   # Set named credentials
jgmcli accounts credentials --list                    # List all credentials
jgmcli accounts credentials --remove work             # Remove credentials
jgmcli accounts add you@gmail.com                     # Add account (default creds)
jgmcli accounts add you@work.com --credentials work   # Add account (named creds)
jgmcli accounts add you@gmail.com --manual            # Browserless OAuth
jgmcli accounts add you@gmail.com --force             # Re-authorize
jgmcli accounts list                                  # List accounts
jgmcli accounts remove you@gmail.com                  # Remove account

# Search threads
jgmcli search you@gmail.com "in:inbox is:unread"
jgmcli search you@gmail.com "from:boss@company.com" --max 50
jgmcli search you@gmail.com "has:attachment after:2024/01/01" --page TOKEN

# View threads
jgmcli thread you@gmail.com 19aea1f2f3532db5
jgmcli thread you@gmail.com 19aea1f2f3532db5 --download

# Labels
jgmcli labels you@gmail.com list
jgmcli labels you@gmail.com abc123 --add Work --remove UNREAD
jgmcli labels you@gmail.com abc123 def456 --add STARRED

# Drafts
jgmcli drafts you@gmail.com list
jgmcli drafts you@gmail.com get r1234567890
jgmcli drafts you@gmail.com get r1234567890 --download
jgmcli drafts you@gmail.com create --to a@x.com --subject "Hi" --body "Hello"
jgmcli drafts you@gmail.com create --to a@x.com --subject "Re: Topic" \
    --body "Reply" --reply-to 19aea1f2f3532db5
jgmcli drafts you@gmail.com send r1234567890
jgmcli drafts you@gmail.com delete r1234567890

# Send directly
jgmcli send you@gmail.com --to a@x.com --subject "Hi" --body "Hello"
jgmcli send you@gmail.com --to a@x.com,b@y.com --cc c@z.com \
    --subject "Hello" --body "Message" --attach file.pdf --attach image.png

# Generate URLs
jgmcli url you@gmail.com 19aea1f2f3532db5 19aea1f2f3532db6
```

### Gmail Query Syntax

```
in:inbox, in:sent, in:drafts, in:trash
is:unread, is:starred, is:important
from:sender@example.com, to:recipient@example.com
subject:keyword, has:attachment, filename:pdf
after:2024/01/01, before:2024/12/31
label:Work, label:UNREAD
Combine: "in:inbox is:unread from:boss@company.com"
```

## Data Storage

```
~/.jgcli/                        # Shared with jgccli, jgdcli
├── credentials.json             # OAuth client credentials (shared)
├── accounts-gmail.json          # Gmail account tokens
└── attachments/                 # Downloaded attachments
```

## Output Format

All list operations output tab-separated values for easy parsing:

```bash
# Search results
ID          DATE             FROM                SUBJECT         LABELS
19aea...    2024-01-15 10:30 sender@example.com  Meeting notes   INBOX,UNREAD

# Labels
ID          NAME        TYPE
INBOX       INBOX       system
Label_1     Work        user
```

## Development

### Running in dev mode

```bash
mvn quarkus:dev
```

### Running tests

```bash
mvn test
```

### Native compilation

```bash
mvn package -Pnative
```

## License

MIT
