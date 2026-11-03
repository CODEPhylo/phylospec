package tiles.input;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;

class ParserTileTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"literal", "named", "arithmetic"})
    void retainsLiteralNamedLiteralAndFoldedArithmeticParts(String input) throws Exception {
        String source =
                switch (input) {
                    case "named" -> "PositiveInteger field = 2\nParser result = parse(delimiter=\"_\", part=field)";
                    case "arithmetic" -> "Parser result = parse(delimiter=\"_\", part=1+1)";
                    default -> "Parser result = parse(delimiter=\"_\", part=2)";
                };
        ParserTile.Parser parser = assertInstanceOf(ParserTile.Parser.class, build(source));
        assertEquals("spA", parser.parse("sample_spA_2002"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsFixedComputedPartFromNumSites(boolean named) throws Exception {
        writeReference();
        String source = "Alignment reference = fromFasta(file=\"reference.fa\")\n"
                + (named ? "PositiveInteger field = numSites(alignment=reference)\n" : "")
                + "Parser result = parse(delimiter=\"_\", part="
                + (named ? "field" : "numSites(alignment=reference)") + ")";
        ParserTile.Parser parser = assertInstanceOf(ParserTile.Parser.class, build(source));
        assertEquals("spA", parser.parse("sample_spA_2002"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fromFasta", "fromNexus"})
    void computedPartExtractsAgeFromBothInputFormats(String reader) throws Exception {
        writeReference();
        List<String> names = List.of("a_0", "b_2");
        String file = reader.equals("fromFasta") ? "samples.fa" : "samples.nex";
        Files.writeString(
                directory.resolve(file),
                reader.equals("fromFasta")
                        ? ">" + names.get(0) + "\nACGT\n>" + names.get(1) + "\nACGA\n"
                        : """
                        #NEXUS
                        begin data;
                            dimensions ntax=2 nchar=4;
                            format datatype=dna missing=? gap=-;
                            matrix
                            %s ACGT
                            %s ACGA
                            ;
                        end;
                        """.formatted(names.get(0), names.get(1)));
        DecoratedAlignment data = assertInstanceOf(DecoratedAlignment.class, build("""
                Alignment reference = fromFasta(file="reference.fa")
                PositiveInteger field = numSites(alignment=reference)
                Alignment data = %s(file="%s", age=parse(delimiter="_", part=field))
                """.formatted(reader, file)));
        assertEquals(names, data.alignment().getTaxaNames());
        assertEquals(names, data.taxonSet().asStringList());
        assertEquals("age", data.ages().getTraitName());
        assertEquals(0.0, data.ages().getValue("a_0"), 1e-12);
        assertEquals(2.0, data.ages().getValue("b_2"), 1e-12);
    }

    @Test
    void namedPartCanStillBeSharedWithAnInputRequiringABoxedInteger() throws Exception {
        Files.writeString(directory.resolve("samples.fa"), ">a_0\nACGT\n>b_2\nACGA\n");
        DecoratedAlignment filtered = assertInstanceOf(DecoratedAlignment.class, build("""
                PositiveInteger field = 2
                Parser extractor = parse(delimiter="_", part=field)
                Alignment data = fromFasta(file="samples.fa", age=extractor)
                Alignment filtered = subset(alignment=data, start=field)
                """));
        assertEquals(3, filtered.alignment().getSiteCount());
        assertEquals(List.of("a_0", "b_2"), filtered.alignment().getTaxaNames());
        assertEquals(List.of("a_0", "b_2"), filtered.taxonSet().asStringList());
        assertEquals("age", filtered.ages().getTraitName());
        assertEquals(0.0, filtered.ages().getValue("a_0"), 1e-12);
        assertEquals(2.0, filtered.ages().getValue("b_2"), 1e-12);
    }

    @Test
    void rejectsStochasticDependencyWithTheExistingFixedPartDiagnostic() {
        // The selected field retains PositiveInteger type while its random index
        // changes which value is selected; rejection must be about stochasticity.
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> build("""
                Vector<PositiveInteger> fields = [1, 2]
                NonNegativeInteger selector ~ Poisson(rate=2.0)
                Parser result = parse(delimiter="_", part=fields[selector])
                """));
        assertTrue(error.getMessage().contains("part"), error.getMessage());
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void coreAndParserConstructorRejectNonpositiveParts(int part) {
        assertThrows(TypeError.class, () -> build("Parser result = parse(delimiter=\"_\", part=" + part + ")"));
        assertThrows(TileApplicationError.class, () -> new ParserTile.DelimiterParser("_", part));
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "|", "*"})
    void delimiterRemainsLiteralRatherThanARegularExpression(String delimiter) throws Exception {
        ParserTile.Parser parser = assertInstanceOf(
                ParserTile.Parser.class, build("Parser result = parse(delimiter=\"" + delimiter + "\", part=2)"));
        assertEquals("spA", parser.parse("sample" + delimiter + "spA" + delimiter + "2002"));
    }

    @Test
    void regexPathStillReturnsTheFirstCaptureOfTheFirstMatch() throws Exception {
        ParserTile.Parser parser =
                assertInstanceOf(ParserTile.Parser.class, build("Parser result = parse(regex=\"_([^_]+)_([0-9]+)\")"));
        assertEquals("spA", parser.parse("sample_spA_2002"));
        assertThrows(TileApplicationError.class, () -> parser.parse("unmatched"));
    }

    private void writeReference() throws Exception {
        Files.writeString(directory.resolve("reference.fa"), ">reference_a\nAC\n>reference_b\nAG\n");
    }

    private Object build(String source) throws Exception {
        Randomizer.setSeed(17321);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(directory)).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        var tiles = evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("parser", directory));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        assertEquals(1, tiles.size());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        return tiles.getFirst().apply(graph, new IdentityHashMap<>());
    }
}
