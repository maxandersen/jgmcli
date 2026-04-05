package dk.xam.jgmcli.model;

public record LabelOperationResult(
    String threadId,
    boolean success,
    String error
) {
    public LabelOperationResult(String threadId, boolean success) {
        this(threadId, success, null);
    }
}
