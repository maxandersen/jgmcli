package dk.xam.jgmcli.model;

public record DownloadedAttachment(
    String messageId,
    String filename,
    String path,
    long size,
    String mimeType,
    boolean cached
) {}
