package org.phylospec.typeresolver.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.workspace.Workspace;

class FromCsvProviderTest {
    @TempDir
    Path directory;

    @Test
    void defaultHeadersExcludeOnlyTheFirstRecord() throws Exception {
        Files.writeString(directory.resolve("metadata.csv"), "taxon,species\na,spA\nb,spB\n");
        assertCount("", "", 2);
    }

    @Test
    void suppliedHeadersRetainTheFirstDataRecordAndShareTheRecordCache() throws Exception {
        Path file = Files.writeString(directory.resolve("metadata.csv"), "a,spA\nb,spB\n");
        assertCount("", "", 1);
        assertCount("", ", headers=[\"taxon\", \"species\"]", 2);
        assertCount("", "", 1);
        assertEquals(1, LightweightFileParsers.parseCsv(file).orElseThrow());
    }

    @Test
    void singleDataRecordIsNotDiscardedWithSuppliedHeaders() throws Exception {
        Files.writeString(directory.resolve("metadata.csv"), "a,spA");
        assertCount("", ", headers=[\"taxon\", \"species\"]", 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyFilesDoNotAcquireAnExtraRecord(boolean suppliedHeaders) throws Exception {
        Files.writeString(directory.resolve("metadata.csv"), "");
        assertCount("", suppliedHeaders ? ", headers=[\"taxon\", \"species\"]" : "", 0);
    }

    @Test
    void quotedMultilineFieldsUseTheSameLogicalRecordCountAsTheReader() throws Exception {
        Files.writeString(
                directory.resolve("metadata.csv"),
                "\ufefftaxon,note\r\na,\"first\r\nsecond\"\r\nb,\"a, b and \"\"c\"\"\"\r\n");
        assertCount("", "", 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"|", "||", "\t"})
    void explicitDelimiterIsUsedAndCacheEntriesDoNotCrossDelimiters(String delimiter) throws Exception {
        Path file = Files.writeString(
                directory.resolve("metadata.csv"), "taxon" + delimiter + "note\na" + delimiter + "\"first\nsecond\"\n");
        assertEquals(1, LightweightFileParsers.parseCsv(file, delimiter, false).orElseThrow());
        assertTrue(LightweightFileParsers.parseCsv(file, ",", false).isEmpty());
        assertEquals(1, LightweightFileParsers.parseCsv(file, delimiter, false).orElseThrow());
        assertCount("String separator = \"" + delimiter + "\"\n", ", delimiter=separator", 1);
    }

    @Test
    void unknownDelimiterLeavesTheLengthUnknownRatherThanAssumingComma() throws Exception {
        Files.writeString(directory.resolve("metadata.csv"), "taxon,species\na,spA\n");
        assertCount(
                "String separator = env(variable=\"PHYLOSPEC_TEST_CSV_DELIMITER\")\n", ", delimiter=separator", null);
    }

    @Test
    void missingMalformedAndOversizedFilesLeaveTheLengthUnknown() throws Exception {
        assertCount("", "", null);
        Files.writeString(directory.resolve("metadata.csv"), "taxon,note\na,\"unterminated\n");
        assertCount("", "", null);
        Files.writeString(directory.resolve("metadata.csv"), "x".repeat(5 * 1024 * 1024 + 1));
        assertCount("", "", null);
    }

    @Test
    void invalidDelimiterDoesNotReuseAValidCachedCount() throws Exception {
        Path file = Files.writeString(directory.resolve("metadata.csv"), "taxon\na\n");
        assertEquals(1, LightweightFileParsers.parseCsv(file).orElseThrow());
        assertTrue(LightweightFileParsers.parseCsv(file, "", false).isEmpty());
        assertCount("", ", delimiter=\"\"", null);
        assertEquals(1, LightweightFileParsers.parseCsv(file).orElseThrow());
    }

    private void assertCount(String prefix, String arguments, Integer expected) throws Exception {
        String source = prefix + "Vector<Map<String, String>> rows = fromCSV(file=\"metadata.csv\"" + arguments
                + ")\nNonNegativeInteger count = num(vector=rows)\n";
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = parser.parse();
        TypeResolver resolver = new TypeResolver(
                new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()), new Workspace(directory));
        resolver.visitStatements(statements);
        var countTypes = resolver.resolveTypeSet(statements.getLast());
        assertFalse(countTypes.isEmpty());
        for (ResolvedType type : countTypes) {
            assertEquals(expected, type.properties().get(VALUE));
        }
        var rowTypes = resolver.resolveTypeSet(statements.get(statements.size() - 2));
        assertFalse(rowTypes.isEmpty());
        for (ResolvedType type : rowTypes) {
            assertEquals(expected, type.properties().get(NUM));
        }
    }
}
