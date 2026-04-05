package dk.xam.jgmcli.cli;

import dk.xam.jgmcli.model.LabelInfo;
import dk.xam.jgmcli.model.LabelOperationResult;
import dk.xam.jgmcli.service.GmailService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static dk.xam.jgmcli.cli.OutputFormatter.*;

@Command(name = "labels", description = "List labels or modify thread labels", mixinStandardHelpOptions = true)
public class LabelsCommand implements Callable<Integer> {

    @Inject
    GmailService service;

    @Parameters(index = "0", description = "Email account")
    String email;

    @Parameters(index = "1..*", arity = "0..*", description = "'list' or thread IDs to modify")
    List<String> args;

    @Option(names = "--add", description = "Labels to add (comma-separated)")
    String addLabels;

    @Option(names = "--remove", description = "Labels to remove (comma-separated)")
    String removeLabels;

    @Override
    public Integer call() throws Exception {
        if (args == null || args.isEmpty()) {
            System.err.println("Usage: jgmcli labels <email> list");
            System.err.println("       jgmcli labels <email> <threadIds...> [--add L] [--remove L]");
            return 1;
        }

        if ("list".equals(args.get(0))) {
            return listLabels();
        } else {
            return modifyLabels();
        }
    }

    private Integer listLabels() throws Exception {
        List<LabelInfo> labels = service.listLabels(email);
        
        List<String[]> rows = new ArrayList<>();
        for (LabelInfo label : labels) {
            rows.add(new String[]{label.id(), label.name(), label.type()});
        }
        
        printTable(new String[]{"ID", "NAME", "TYPE"}, rows);
        return 0;
    }

    private Integer modifyLabels() throws Exception {
        List<String> threadIds = args;
        
        if (addLabels == null && removeLabels == null) {
            System.err.println("Error: Specify --add or --remove labels");
            return 1;
        }
        
        Map<String, String> nameToId = service.getLabelNameToIdMap(email);
        
        List<String> toAdd = addLabels != null 
            ? service.resolveLabelIds(Arrays.asList(addLabels.split(",")), nameToId)
            : List.of();
        List<String> toRemove = removeLabels != null
            ? service.resolveLabelIds(Arrays.asList(removeLabels.split(",")), nameToId)
            : List.of();
        
        List<LabelOperationResult> results = service.modifyLabels(email, threadIds, toAdd, toRemove);
        
        for (LabelOperationResult result : results) {
            System.out.println(result.threadId() + ": " + (result.success() ? "ok" : result.error()));
        }
        
        return 0;
    }
}
