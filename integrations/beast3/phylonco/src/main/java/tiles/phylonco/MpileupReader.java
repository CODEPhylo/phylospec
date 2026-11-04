package tiles.phylonco;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads nucleotide read counts from a samtools mpileup file, following LPhy's
 * {@code readMpileup} + {@code toReadCount} (lphy.base.function.io.ReadMpileup and
 * lphy.base.evolution.PileupSite.translateRead). No site filtering is applied.
 *
 * <p>Each line is {@code chrom pos ref} followed by three columns per cell
 * ({@code depth bases qualities}). Lines with too few columns are skipped as a whole
 * (LPhy records their position and skips only the cell data, which misaligns sites).
 */
public final class MpileupReader {

    /** Counts per cell, site and base (A, C, G, T). */
    public record Counts(List<String> cells, int[][][] counts, List<String> sites) {
        public int numSites() {
            return this.sites.size();
        }

        /** The phylonco ReadCount string: one line per cell, sites separated by ',', bases by ':'. */
        public String toReadCountString() {
            StringBuilder builder = new StringBuilder();
            for (int cell = 0; cell < this.cells.size(); cell++) {
                if (cell > 0) builder.append('\n');
                for (int site = 0; site < this.numSites(); site++) {
                    if (site > 0) builder.append(',');
                    int[] c = this.counts[cell][site];
                    builder.append(c[0]).append(':').append(c[1]).append(':').append(c[2]).append(':').append(c[3]);
                }
            }
            return builder.toString();
        }
    }

    private MpileupReader() {}

    public static Counts read(Path file, List<String> cells) throws IOException {
        List<int[][]> sites = new ArrayList<>(); // per site: [cell][base]
        List<String> siteNames = new ArrayList<>();
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 3 + 3 * cells.size()) continue;

                int ref = baseIndex(parts[2].trim().isEmpty() ? 'N' : parts[2].trim().charAt(0));
                if (ref < 0) {
                    throw new IOException("Line " + lineNumber + ": reference base '" + parts[2]
                            + "' is not A, C, G or T.");
                }

                int[][] site = new int[cells.size()][];
                for (int cell = 0; cell < cells.size(); cell++) {
                    site[cell] = countBases(ref, parts[3 + 3 * cell + 1]);
                }
                sites.add(site);
                siteNames.add(parts[0] + ":" + parts[1]);
            }
        }

        int[][][] counts = new int[cells.size()][sites.size()][];
        for (int s = 0; s < sites.size(); s++) {
            for (int cell = 0; cell < cells.size(); cell++) {
                counts[cell][s] = sites.get(s)[cell];
            }
        }
        return new Counts(List.copyOf(cells), counts, siteNames);
    }

    /** Same rules as LPhy's PileupSite.translateRead. */
    static int[] countBases(int ref, String bases) {
        int[] counts = new int[4];
        int i = 0;
        while (i < bases.length()) {
            char c = bases.charAt(i);
            switch (c) {
                case '.', ',' -> {
                    counts[ref]++;
                    i++;
                }
                case '^' -> i += 2; // read start followed by its mapping quality
                case '$' -> i++; // read end
                case '+', '-' -> { // indel: length then sequence
                    i++;
                    int start = i;
                    while (i < bases.length() && Character.isDigit(bases.charAt(i))) i++;
                    i += Integer.parseInt(bases.substring(start, i));
                }
                case '<', '>', '*' -> i++; // reference skip or deletion
                default -> {
                    int base = baseIndex(c);
                    if (base >= 0) counts[base]++;
                    i++;
                }
            }
        }
        return counts;
    }

    static int baseIndex(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'A' -> 0;
            case 'C' -> 1;
            case 'G' -> 2;
            case 'T' -> 3;
            default -> -1;
        };
    }
}
