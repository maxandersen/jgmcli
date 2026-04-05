package dk.xam.jgmcli.model;

import java.util.List;

public record ThreadSearchResult(
    List<ThreadInfo> threads,
    String nextPageToken
) {
    public record ThreadInfo(
        String id,
        String historyId,
        List<MessageInfo> messages
    ) {}

    public record MessageInfo(
        String id,
        String threadId,
        List<String> labelIds,
        String snippet,
        String historyId,
        String internalDate,
        String from,
        String to,
        String subject,
        String date,
        boolean hasAttachments
    ) {}
}
