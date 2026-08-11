package org.phylospec.typeresolver.properties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.phylospec.workspace.Workspace;

/**
 * Cheaply extracts type properties (taxa counts, site counts, branch counts, row counts) from
 * small alignment, tree, and CSV files without fully parsing them into their normal in-memory
 * representations. Results are cached per file and invalidated when the file's size or
 * modification time changes.
 */
final class LightweightFileParsers {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_CACHED_FILES = 256;
    private static final int MAX_NEWICK_LENGTH = 5 * 1024 * 1024;
    private static final int MAX_NEWICK_DEPTH = 10_000;
    private static final ConcurrentHashMap<CacheKey, CacheEntry> FILE_CACHE = new ConcurrentHashMap<>();
    private static final Pattern NTAX_PATTERN = Pattern.compile("\\bntax\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NCHAR_PATTERN = Pattern.compile("\\bnchar\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXUS_TREE_PATTERN =
            Pattern.compile("\\b(?:tree|utree)\\b[^=;]*=\\s*(.+?;)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private LightweightFileParsers() {}

    static Optional<Integer> parseCsv(Path path) {
        return parseCached(path, ParserKind.CSV, LightweightFileParsers::parseCsvContents);
    }

    private static Optional<Integer> parseCsvContents(String csv) {
        int records = 0;
        boolean inQuotes = false;
        boolean recordHasContent = false;

        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);
            if (current == '"') {
                recordHasContent = true;
                if (inQuotes && index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (!inQuotes && (current == '\n' || current == '\r')) {
                if (recordHasContent) records++;
                recordHasContent = false;
                if (current == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                    index++;
                }
            } else if (!Character.isWhitespace(current)) {
                recordHasContent = true;
            }
        }

        if (inQuotes) return Optional.empty();
        if (recordHasContent) records++;
        return Optional.of(Math.max(0, records - 1));
    }

    static Optional<AlignmentProperties> parseFasta(Path path) {
        return parseCached(path, ParserKind.FASTA, LightweightFileParsers::parseFastaContents);
    }

    private static Optional<AlignmentProperties> parseFastaContents(String fasta) {
        try (BufferedReader reader = new BufferedReader(new StringReader(fasta))) {
            int numTaxa = 0;
            int numSites = -1;
            int currentSequenceLength = -1;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    if (line.substring(1).trim().isEmpty()) return Optional.empty();
                    if (currentSequenceLength >= 0) {
                        if (numSites >= 0 && currentSequenceLength != numSites) {
                            return Optional.empty();
                        }
                        numSites = currentSequenceLength;
                        numTaxa++;
                    }
                    currentSequenceLength = 0;
                } else if (!line.isBlank()) {
                    if (currentSequenceLength < 0) return Optional.empty();
                    currentSequenceLength += countNonWhitespace(line);
                }
            }

            if (currentSequenceLength < 0) return Optional.empty();
            if (numSites >= 0 && currentSequenceLength != numSites) return Optional.empty();
            numTaxa++;
            numSites = currentSequenceLength;
            return Optional.of(new AlignmentProperties(numSites, numTaxa));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<AlignmentProperties> parseNexus(Path path) {
        return parseCached(path, ParserKind.NEXUS, LightweightFileParsers::parseNexusContents);
    }

    private static Optional<AlignmentProperties> parseNexusContents(String nexusContents) {
        try {
            String nexus = removeBracketComments(nexusContents);
            Matcher ntaxMatcher = NTAX_PATTERN.matcher(nexus);
            Matcher ncharMatcher = NCHAR_PATTERN.matcher(nexus);
            if (!ntaxMatcher.find() || !ncharMatcher.find()) return Optional.empty();

            int numTaxa = Integer.parseInt(ntaxMatcher.group(1));
            int numSites = Integer.parseInt(ncharMatcher.group(1));
            return Optional.of(new AlignmentProperties(numSites, numTaxa));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<TreeProperties> parseTree(Path path) {
        return parseCached(path, ParserKind.TREE, LightweightFileParsers::parseTreeContents);
    }

    private static Optional<TreeProperties> parseTreeContents(String contents) {
        String treeContents = contents;
        try {
            if (treeContents.stripLeading().regionMatches(true, 0, "#NEXUS", 0, 6)) {
                treeContents = removeBracketComments(treeContents);
                Matcher matcher = NEXUS_TREE_PATTERN.matcher(treeContents);
                if (!matcher.find()) return Optional.empty();
                treeContents = matcher.group(1);
            }
            return parseNewick(treeContents);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<TreeProperties> parseNewick(String newick) {
        if (newick == null || newick.length() > MAX_NEWICK_LENGTH) return Optional.empty();

        try {
            return Optional.of(new NewickParser(newick).parse());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<Path> resolveSmallFile(String fileName, Workspace workspace) {
        try {
            Path directPath = Path.of(fileName);
            if (isSmallRegularFile(directPath)) return Optional.of(directPath);

            for (Path folder : workspace.FOLDERS) {
                Path workspacePath = folder.resolve(fileName);
                if (isSmallRegularFile(workspacePath)) return Optional.of(workspacePath);
            }
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static <T> Optional<T> parseCached(Path path, ParserKind parserKind, Function<String, Optional<T>> parser) {
        try {
            CacheKey key = new CacheKey(path.toAbsolutePath().normalize(), parserKind);
            evictIfFull(key);
            CacheEntry entry = FILE_CACHE.compute(key, (ignored, cached) -> loadCacheEntry(key.path(), cached, parser));
            if (entry == null) return Optional.empty();
            return castResult(entry.result());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static <T> CacheEntry loadCacheEntry(Path path, CacheEntry cached, Function<String, Optional<T>> parser) {
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                BasicFileAttributes attributes = readSmallFileAttributes(path);
                if (attributes == null) return null;

                FileSignature signature = FileSignature.from(attributes);
                if (cached != null && cached.signature().equals(signature)) return cached;

                String contents = Files.readString(path, StandardCharsets.UTF_8);
                BasicFileAttributes attributesAfterRead = readSmallFileAttributes(path);
                if (attributesAfterRead == null) return null;
                FileSignature signatureAfterRead = FileSignature.from(attributesAfterRead);
                if (signature.equals(signatureAfterRead)) {
                    return new CacheEntry(signature, parser.apply(contents));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static BasicFileAttributes readSmallFileAttributes(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile() || attributes.size() > MAX_FILE_SIZE) return null;
        return attributes;
    }

    private static void evictIfFull(CacheKey key) {
        if (FILE_CACHE.size() < MAX_CACHED_FILES || FILE_CACHE.containsKey(key)) return;
        FILE_CACHE.keySet().stream().findAny().ifPresent(FILE_CACHE::remove);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> castResult(Optional<?> result) {
        return (Optional<T>) result;
    }

    private static boolean isSmallRegularFile(Path path) throws IOException {
        return Files.isRegularFile(path) && Files.size(path) <= MAX_FILE_SIZE;
    }

    private static int countNonWhitespace(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) count++;
        }
        return count;
    }

    private static String removeBracketComments(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int commentDepth = 0;
        char quote = 0;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (commentDepth > 0) {
                if (current == '[') commentDepth++;
                if (current == ']') commentDepth--;
            } else if (quote != 0) {
                result.append(current);
                if (current == quote) {
                    if (index + 1 < value.length() && value.charAt(index + 1) == quote) {
                        result.append(value.charAt(++index));
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '[') {
                commentDepth = 1;
            } else {
                result.append(current);
                if (current == '\'' || current == '"') quote = current;
            }
        }

        if (commentDepth != 0 || quote != 0) throw new IllegalArgumentException();
        return result.toString();
    }

    record AlignmentProperties(int numSites, int numTaxa) {}

    record TreeProperties(int numBranches, int numTaxa) {}

    private enum ParserKind {
        CSV,
        FASTA,
        NEXUS,
        TREE
    }

    private record CacheKey(Path path, ParserKind parserKind) {}

    private record FileSignature(long size, FileTime lastModifiedTime, Object fileKey) {

        private static FileSignature from(BasicFileAttributes attributes) {
            return new FileSignature(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
        }
    }

    private record CacheEntry(FileSignature signature, Optional<?> result) {}

    private static final class NewickParser {

        private final String value;
        private int index;
        private int depth;
        private int nodes;
        private int taxa;
        private boolean expectingSubtree = true;

        private NewickParser(String value) {
            this.value = value;
        }

        private TreeProperties parse() {
            skipIgnored();
            while (index < value.length()) {
                skipIgnored();
                if (index >= value.length()) break;

                char current = value.charAt(index);
                if (expectingSubtree) {
                    if (current == '(') {
                        depth++;
                        if (depth > MAX_NEWICK_DEPTH) throw new IllegalArgumentException();
                        nodes++;
                        index++;
                    } else {
                        readLabel();
                        taxa++;
                        nodes++;
                        readSuffix();
                        expectingSubtree = false;
                    }
                } else if (current == ',') {
                    if (depth == 0) throw new IllegalArgumentException();
                    index++;
                    expectingSubtree = true;
                } else if (current == ')') {
                    if (depth == 0) throw new IllegalArgumentException();
                    depth--;
                    index++;
                    readSuffix();
                } else if (current == ';') {
                    if (depth != 0 || nodes == 0) throw new IllegalArgumentException();
                    index++;
                    skipIgnored();
                    if (index != value.length()) throw new IllegalArgumentException();
                    return new TreeProperties(nodes - 1, taxa);
                } else {
                    throw new IllegalArgumentException();
                }
            }
            throw new IllegalArgumentException();
        }

        private void readSuffix() {
            skipIgnored();
            if (index < value.length() && !isStructural(value.charAt(index))) readLabel();
            skipIgnored();
            if (index < value.length() && value.charAt(index) == ':') {
                index++;
                skipIgnored();
                readLabel();
            }
            skipIgnored();
        }

        private void readLabel() {
            if (index >= value.length()) throw new IllegalArgumentException();
            char first = value.charAt(index);
            if (first == '\'' || first == '"') {
                char quote = first;
                index++;
                boolean closed = false;
                while (index < value.length()) {
                    char current = value.charAt(index++);
                    if (current == quote) {
                        if (index < value.length() && value.charAt(index) == quote) {
                            index++;
                        } else {
                            closed = true;
                            break;
                        }
                    }
                }
                if (!closed) throw new IllegalArgumentException();
                return;
            }

            int start = index;
            while (index < value.length()) {
                char current = value.charAt(index);
                if (Character.isWhitespace(current) || isStructural(current) || current == '[' || current == ']') {
                    break;
                }
                index++;
            }
            if (index == start) throw new IllegalArgumentException();
        }

        private void skipIgnored() {
            boolean skipped;
            do {
                skipped = false;
                while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                    index++;
                    skipped = true;
                }
                if (index < value.length() && value.charAt(index) == '[') {
                    int commentDepth = 1;
                    index++;
                    while (index < value.length() && commentDepth > 0) {
                        char current = value.charAt(index++);
                        if (current == '[') commentDepth++;
                        if (current == ']') commentDepth--;
                    }
                    if (commentDepth != 0) throw new IllegalArgumentException();
                    skipped = true;
                }
            } while (skipped);
        }

        private boolean isStructural(char value) {
            return value == '(' || value == ')' || value == ',' || value == ':' || value == ';';
        }
    }
}
