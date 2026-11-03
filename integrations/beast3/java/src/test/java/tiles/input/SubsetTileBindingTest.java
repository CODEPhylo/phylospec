package tiles.input;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import beast.base.spec.evolution.alignment.FilteredAlignment;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
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

class SubsetTileBindingTest {
    @TempDir
    Path directory;

    private Path alignmentFile;

    @BeforeEach
    void writeAlignment() throws Exception {
        alignmentFile = directory.resolve("alignment.nex");
        Files.writeString(alignmentFile, """
                #NEXUS
                begin data;
                    dimensions ntax=2 nchar=12;
                    format datatype=dna missing=? gap=-;
                    matrix
                        taxon_1 ACGTACGTACGT
                        taxon_2 TGCATGCATGCA
                    ;
                end;
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"literal", "named", "derived"})
    void fixedIntegerRepresentationsSelectTheSameColumns(String representation) throws Exception {
        String declarations = "";
        String arguments;
        switch (representation) {
            case "literal" -> arguments = "start=2, end=12, codonPosition=2";
            case "named" -> {
                declarations = "PositiveInteger start=2\nPositiveInteger end=12\nPositiveInteger position=2\n";
                arguments = "start=start, end=end, codonPosition=position";
            }
            case "derived" ->
                arguments = "start=numTaxa(alignment=source), end=numSites(alignment=source), "
                        + "codonPosition=numTaxa(alignment=source)";
            default -> throw new AssertionError(representation);
        }
        DecoratedAlignment result = build(declarations, arguments);
        assertEquals(4, result.alignment().getSiteCount());
        assertEquals("CATG", result.alignment().getSequenceAsString("taxon_1"));
        assertEquals("GTAC", result.alignment().getSequenceAsString("taxon_2"));
    }

    @Test
    void omittedOptionalArgumentsKeepTheirExistingDefaults() throws Exception {
        assertEquals("ACGTACGTACGT", build("", "").alignment().getSequenceAsString("taxon_1"));
        assertEquals(
                "ACGTACGTACGT",
                build("", "end=numSites(alignment=source)").alignment().getSequenceAsString("taxon_1"));
        assertEquals(
                "CGTACGTACGT",
                build("", "start=numTaxa(alignment=source)").alignment().getSequenceAsString("taxon_1"));
        assertEquals(
                "CATG",
                build("", "codonPosition=numTaxa(alignment=source)").alignment().getSequenceAsString("taxon_1"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "start=numSites(alignment=source), end=numTaxa(alignment=source) | bigger than your end index",
                "end=13 | bigger than the total number of sites",
                "start=1, end=1, codonPosition=numTaxa(alignment=source) | contains no sites",
                "codonPosition=4 | outside the range from one to three"
            })
    void boundValuesStillPassThroughExistingValidation(String arguments, String message) {
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> build("", arguments));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"end=0", "start=-1"})
    void coreStillRequiresPositiveIntegerIndices(String arguments) {
        assertThrows(TypeError.class, () -> build("", arguments));
    }

    @Test
    void stochasticInputRemainsUnsupportedAfterCoreTypeChecking() {
        // numTaxa(tree) has the required PositiveInteger core type, but its source is stochastic.
        String declarations = "Tree tree ~ Yule(birthRate=1.0, taxa=taxa(alignment=source))\n";
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> build(declarations, "end=numTaxa(tree=tree)"));
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
        assertTrue(error.getMessage().contains("'end'"), error.getMessage());
    }

    @Test
    void existingIndexedSyntaxCanSelectAllThreeCodonPositions() throws Exception {
        String source =
                sourceDeclaration() + "Alignment result[i] = subset(alignment=source, codonPosition=i) for i in 1:3\n";
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        var resultTile = evaluator.getBestTiling(statements).stream()
                .filter(tile -> tile.getRootNode() instanceof Stmt.Indexed)
                .findFirst()
                .orElseThrow();
        BEASTState state = new BEASTState("subset-indexed-test");
        evaluator.applyBestTiling(state);
        List<?> partitions = assertInstanceOf(List.class, resultTile.apply(state, new IdentityHashMap<>()));
        state.initializeBEASTObjects();
        assertEquals(3, partitions.size());
        DecoratedAlignment first = assertInstanceOf(DecoratedAlignment.class, partitions.getFirst());
        assertNotNull(first.ages());
        String[] expectedFirstTaxon = {"ATGC", "CATG", "GCAT"};
        String[] expectedSecondTaxon = {"TACG", "GTAC", "CGTA"};
        for (int i = 0; i < partitions.size(); i++) {
            DecoratedAlignment part = assertInstanceOf(DecoratedAlignment.class, partitions.get(i));
            assertEquals(4, part.alignment().getSiteCount());
            assertEquals(expectedFirstTaxon[i], part.alignment().getSequenceAsString("taxon_1"));
            assertEquals(expectedSecondTaxon[i], part.alignment().getSequenceAsString("taxon_2"));
            assertEquals(List.of("taxon_1", "taxon_2"), part.alignment().getTaxaNames());
            assertSame(first.taxonSet(), part.taxonSet());
            assertSame(first.ages(), part.ages());
        }
        assertTrue(state.stateNodes.isEmpty());
        assertTrue(state.calculationNodes.isEmpty());
        assertTrue(state.priorDistributions.isEmpty());
        assertTrue(state.likelihoodDistributions.isEmpty());
        assertTrue(state.operators.isEmpty());
    }

    private String sourceDeclaration() {
        String path = alignmentFile.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return "Alignment source = fromNexus(file=\"" + path + "\", age=parse(delimiter=\"_\", part=2))\n";
    }

    private DecoratedAlignment build(String declarations, String arguments) throws Exception {
        String source = sourceDeclaration()
                + declarations
                + "Alignment result = subset(alignment=source"
                + (arguments.isEmpty() ? "" : ", " + arguments) + ")\n";
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        var bestTiles = evaluator.getBestTiling(statements);
        var resultTile = bestTiles.stream()
                .filter(tile ->
                        tile.getRootNode() instanceof Stmt.Assignment assignment && assignment.name.equals("result"))
                .findFirst()
                .orElseThrow();
        SubsetTile subset = assertInstanceOf(
                SubsetTile.class,
                assertDoesNotThrow(resultTile::getWiredUpInputs).values().stream()
                        .filter(SubsetTile.class::isInstance)
                        .findFirst()
                        .orElseThrow());
        BEASTState state = new BEASTState("subset-binding-test");
        evaluator.applyBestTiling(state);
        DecoratedAlignment result =
                assertInstanceOf(DecoratedAlignment.class, resultTile.apply(state, new IdentityHashMap<>()));
        // Tile application is memoized: this is the same source wrapper supplied to subset.
        DecoratedAlignment original = subset.alignmentInput.apply(state, new IdentityHashMap<>());
        state.initializeBEASTObjects();
        FilteredAlignment filtered = assertInstanceOf(FilteredAlignment.class, result.alignment());
        assertSame(original.alignment(), filtered.alignmentInput.get());
        assertSame(original.taxonSet(), result.taxonSet());
        assertNotNull(original.ages());
        assertSame(original.ages(), result.ages());
        assertEquals(List.of("taxon_1", "taxon_2"), result.alignment().getTaxaNames());
        assertEquals(12, original.alignment().getSiteCount());
        assertEquals("ACGTACGTACGT", original.alignment().getSequenceAsString("taxon_1"));
        assertEquals("TGCATGCATGCA", original.alignment().getSequenceAsString("taxon_2"));
        assertTrue(state.stateNodes.isEmpty());
        assertTrue(state.calculationNodes.isEmpty());
        assertTrue(state.priorDistributions.isEmpty());
        assertTrue(state.likelihoodDistributions.isEmpty());
        assertTrue(state.operators.isEmpty());
        return result;
    }
}
