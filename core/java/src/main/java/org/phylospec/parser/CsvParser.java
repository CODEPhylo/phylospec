package org.phylospec.parser;

import java.util.ArrayList;
import java.util.List;

/** Shared CSV record parsing for input adapters and static row-count inference. */
public final class CsvParser {
    private CsvParser() {}

    /**
     * Reads literal delimiters, double-quoted fields, doubled quotes, and embedded newlines.
     * Field whitespace is preserved. Empty physical records outside quotes are ignored.
     * The first record is returned unchanged; the caller decides whether it is a header.
     */
    public static List<List<String>> parse(String contents, String delimiter) {
        if (delimiter == null
                || delimiter.isEmpty()
                || delimiter.indexOf('"') >= 0
                || delimiter.indexOf('\r') >= 0
                || delimiter.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "The CSV delimiter must be non-empty and contain no quotes or newlines.");
        }

        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean started = false;
        int index = contents.startsWith("\uFEFF") ? 1 : 0;

        while (index < contents.length()) {
            char current = contents.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < contents.length() && contents.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 2;
                    } else {
                        quoted = false;
                        closedQuote = true;
                        index++;
                    }
                } else {
                    field.append(current);
                    index++;
                }
            } else if (contents.startsWith(delimiter, index)) {
                record.add(field.toString());
                field.setLength(0);
                started = false;
                closedQuote = false;
                index += delimiter.length();
            } else if (current == '\r' || current == '\n') {
                if (started || !record.isEmpty()) {
                    record.add(field.toString());
                    records.add(List.copyOf(record));
                }
                record.clear();
                field.setLength(0);
                started = false;
                closedQuote = false;
                index += current == '\r' && index + 1 < contents.length() && contents.charAt(index + 1) == '\n' ? 2 : 1;
            } else if (closedQuote) {
                throw invalid(records.size() + 1, "Expected a delimiter or newline after a closing quote.");
            } else if (current == '"') {
                if (started) throw invalid(records.size() + 1, "A quote must appear at the start of a field.");
                quoted = true;
                started = true;
                index++;
            } else {
                field.append(current);
                started = true;
                index++;
            }
        }

        if (quoted) throw invalid(records.size() + 1, "Unclosed quoted field.");
        if (started || !record.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return List.copyOf(records);
    }

    private static IllegalArgumentException invalid(int record, String message) {
        return new IllegalArgumentException("CSV record " + record + ": " + message);
    }
}
