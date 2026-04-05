package dk.xam.jgmcli.cli;

import java.util.List;

public class OutputFormatter {

    public static void printTable(String[] headers, List<String[]> rows) {
        System.out.println(String.join("\t", headers));
        for (String[] row : rows) {
            System.out.println(String.join("\t", row));
        }
    }

    public static void printKeyValue(String key, String value) {
        System.out.println(key + ": " + value);
    }

    public static void printKeyValue(String key, Object value) {
        printKeyValue(key, value != null ? value.toString() : "");
    }

    public static String orEmpty(String value) {
        return value != null ? value : "";
    }

    public static String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static String formatSize(long bytes) {
        if (bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = (int) Math.floor(Math.log(bytes) / Math.log(1024));
        i = Math.min(i, units.length - 1);
        double size = bytes / Math.pow(1024, i);
        return i > 0 ? String.format("%.1f %s", size, units[i]) : String.format("%.0f %s", size, units[i]);
    }

    public static String sanitize(String value) {
        return value != null ? value.replace("\t", " ").replace("\n", " ").replace("\r", "") : "";
    }
}
