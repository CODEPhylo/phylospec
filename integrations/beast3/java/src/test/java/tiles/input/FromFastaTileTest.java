package tiles.input;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.alignment.Alignment;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;

class FromFastaTileTest {

    @TempDir
    Path directory;

    private record Built(BEASTState graph, List<Tile<?, BEASTState>> tiles) {}

    @Test
    void readsRelativePathMultilineSequencesAndHeaderDescriptions() throws Exception {
        Path dataDirectory = Files.createDirectories(directory.resolve("data"));
        Files.writeString(dataDirectory.resolve("input.fa"), """
                >a first sample
                ac gt
                rn-?

                >b second sample
                ACGT
                ACGT
                """);

        DecoratedAlignment data = read("Alignment data = fromFasta(file=\"data/input.fa\")");

        assertEquals(List.of("a", "b"), data.alignment().getTaxaNames());
        assertEquals(2, data.alignment().getTaxonCount());
        assertEquals(8, data.alignment().getSiteCount());
        assertEquals("ACGTRN-?", data.alignment().getSequenceAsString("a"));
        assertEquals("ACGTACGT", data.alignment().getSequenceAsString("b"));
        assertEquals(List.of("a", "b"), data.taxonSet().asStringList());
        assertSame(data.alignment(), data.taxonSet().alignmentInput.get());
        assertNull(data.ages());
    }

    @ParameterizedTest
    @CsvSource({"age,0,2,0.0,2.0", "date,2000,2002,2.0,0.0"})
    void samplingMetadataMatchesTheExistingNexusPath(
            String kind, String first, String second, double firstAge, double secondAge) throws Exception {
        String firstTaxon = "a_" + first;
        String secondTaxon = "b_" + second;

        Files.writeString(directory.resolve("input.fa"), ">" + firstTaxon + "\nACGT\n>" + secondTaxon + "\nACGA\n");
        Files.writeString(directory.resolve("input.nex"), nexus(firstTaxon + " ACGT\n" + secondTaxon + " ACGA", 2, 4));

        DecoratedAlignment fasta = read(metadataSource("fromFasta", "input.fa", kind));
        DecoratedAlignment nexus = read(metadataSource("fromNexus", "input.nex", kind));

        assertNotNull(fasta.ages());
        assertNotNull(nexus.ages());
        assertEquals(kind, fasta.ages().getTraitName());
        assertEquals(first, fasta.ages().getStringValue(firstTaxon));
        assertEquals(second, fasta.ages().getStringValue(secondTaxon));
        assertEquals(firstAge, fasta.ages().getValue(firstTaxon), 1e-12);
        assertEquals(secondAge, fasta.ages().getValue(secondTaxon), 1e-12);
        assertSame(fasta.taxonSet(), fasta.ages().taxaInput.get());

        for (String taxon : fasta.alignment().getTaxaNames()) {
            assertEquals(nexus.ages().getValue(taxon), fasta.ages().getValue(taxon), 1e-12);
            assertEquals(nexus.ages().getStringValue(taxon), fasta.ages().getStringValue(taxon));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "ACGT\n",
                ">\nACGT\n",
                ">a\n",
                ">a\nACGT\n>b\n",
                ">a first\nACGT\n>a second\nACGT\n",
                ">a\nACGT\n>b\nACG\n",
                ">a\nACGE\n"
            })
    void rejectsMalformedOrUnalignedFasta(String contents) throws Exception {
        Files.writeString(directory.resolve("invalid.fa"), contents);

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> read("Alignment data = fromFasta(file=\"invalid.fa\")"));

        assertTrue(error.getMessage().contains("FASTA"));
    }

    @Test
    void reportsMissingInputFile() {
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> read("Alignment data = fromFasta(file=\"missing.fa\")"));

        assertTrue(error.getMessage().contains("missing.fa"));
    }

    @Test
    void invalidNucleotideReportsFastaPathAndNativeValidationReason() throws Exception {
        Files.writeString(directory.resolve("invalid.fa"), ">a\nACGE\n");

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> read("Alignment data = fromFasta(file=\"invalid.fa\")"));

        assertEquals(TileApplicationError.class, error.getClass());
        assertTrue(error.getMessage().contains("Invalid nucleotide FASTA alignment."));
        assertTrue(error.getMessage().contains(directory.resolve("invalid.fa").toString()));
        assertTrue(error.getMessage().contains("Unknown code found in sequence: E"));
    }

    @ParameterizedTest
    @CsvSource({
        "fromFasta,age,bad", "fromFasta,age,NaN", "fromFasta,age,Infinity", "fromFasta,age,-1",
                "fromFasta,date,not-a-date",
        "fromNexus,age,bad", "fromNexus,age,NaN", "fromNexus,age,Infinity", "fromNexus,age,-1",
                "fromNexus,date,not-a-date"
    })
    void rejectsInvalidSamplingMetadata(String reader, String kind, String value) throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">a_" + value + "\nACGT\n>b_0\nACGA\n");
        Files.writeString(directory.resolve("input.nex"), nexus("a_" + value + " ACGT\nb_0 ACGA", 2, 4));
        String file = reader.equals("fromFasta") ? "input.fa" : "input.nex";

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> read(metadataSource(reader, file, kind)));

        assertTrue(error.getMessage().contains("Invalid sampling metadata"));
        assertTrue(error.getMessage().contains(directory.resolve(file).toString()));
        assertTrue(error.getMessage().contains("a_" + value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a,b_0", "a=b_0"})
    void rejectsTaxonNamesThatCannotBeEncodedInTraitSet(String name) throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">" + name + "\nACGT\n>b_2\nACGA\n");

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> read(metadataSource("fromFasta", "input.fa", "age")));

        assertTrue(error.getMessage().contains("Invalid sampling metadata"));
    }

    @Test
    void readsSpeciesNamesWithoutRenamingOrMergingSamples() throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">a_species\nACGT\n>b_species\nACGA\n");

        DecoratedAlignment data = read("""
                        Alignment data = fromFasta(
                            file="input.fa",
                            speciesName=parse(delimiter="_", part=2))
                        """);
        assertEquals(Map.of("a_species", "species", "b_species", "species"), data.speciesNames());
        assertEquals(List.of("a_species", "b_species"), data.alignment().getTaxaNames());
        assertEquals(2, data.alignment().getTaxonCount());
        assertEquals("ACGT", data.alignment().getSequenceAsString("a_species"));
        assertEquals("ACGA", data.alignment().getSequenceAsString("b_species"));
        assertNull(data.ages());
    }

    @Test
    void speciesMetadataIsCopiedWithoutReplacingNativeAlignmentInputs() throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">a_0\nACGT\n>b_1\nACGA\n");
        DecoratedAlignment original = read(metadataSource("fromFasta", "input.fa", "age"));
        Map<String, String> mutable = new HashMap<>(Map.of("a_0", "spA", "b_1", "spA"));
        DecoratedAlignment decorated =
                new DecoratedAlignment(original.alignment(), original.taxonSet(), original.ages(), mutable);
        mutable.put("a_0", "changed");

        assertEquals(Map.of("a_0", "spA", "b_1", "spA"), decorated.speciesNames());
        assertThrows(
                UnsupportedOperationException.class,
                () -> decorated.speciesNames().clear());
        assertSame(original.alignment(), decorated.alignment());
        assertSame(original.taxonSet(), decorated.taxonSet());
        assertSame(original.ages(), decorated.ages());
    }

    @Test
    void nexusNamesArePreservedForSpeciesButMustBeRepresentableInSamplingTraits() throws Exception {
        Files.writeString(directory.resolve("input.nex"), nexus("'a  b_0' ACGT\nc_1 ACGA", 2, 4));
        DecoratedAlignment data = read("""
                Alignment data = fromNexus(file="input.nex", speciesName=parse(regex="^(.*)_"))
                """);
        assertEquals(List.of("a  b_0", "c_1"), data.alignment().getTaxaNames());
        assertEquals(Map.of("a  b_0", "a  b", "c_1", "c"), data.speciesNames());

        TileApplicationError error = assertThrows(TileApplicationError.class, () -> read("""
                Alignment data = fromNexus(file="input.nex", age=parse(regex="([01])$"))
                """));
        assertTrue(error.getMessage().contains("Invalid sampling metadata"));
        assertTrue(error.getMessage().contains("a  b_0"));
        assertTrue(error.getMessage().contains(directory.resolve("input.nex").toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fromFasta", "fromNexus"})
    void serializesTheSameNormalizedSamplingValuesThatWereValidated(String reader) throws Exception {
        String first = "a_\u20030\u2003_sample";
        String second = "b_\u20032\u2003_sample";
        Files.writeString(directory.resolve("input.fa"), ">" + first + "\nACGT\n>" + second + "\nACGA\n");
        Files.writeString(directory.resolve("input.nex"), nexus("'" + first + "' ACGT\n'" + second + "' ACGA", 2, 4));
        String file = reader.equals("fromFasta") ? "input.fa" : "input.nex";
        DecoratedAlignment data = read(metadataSource(reader, file, "age"));

        assertEquals(List.of(first, second), data.alignment().getTaxaNames());
        assertEquals("0", data.ages().getStringValue(first));
        assertEquals("2", data.ages().getStringValue(second));
        assertEquals(0.0, data.ages().getValue(first));
        assertEquals(2.0, data.ages().getValue(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fromFasta", "fromNexus"})
    void speciesMetadataDoesNotChangeFixedTreeHkyLikelihood(String reader) throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">a\nACGTRN-?\n>b\nACGTACGT\n>c\nACGTACGA\n");
        Files.writeString(directory.resolve("input.nex"), nexus("a ACGTRN-?\nb ACGTACGT\nc ACGTACGA", 3, 8));
        String file = reader.equals("fromFasta") ? "input.fa" : "input.nex";
        String source = model(reader, file);
        Built withoutMetadata = build(source);
        Built withMetadata = build(
                source.replace("file=\"" + file + "\"", "file=\"" + file + "\", speciesName=parse(regex=\"([abc])\")"));

        TreeLikelihood reference = likelihood(withoutMetadata);
        TreeLikelihood actual = likelihood(withMetadata);
        double expected = reference.calculateLogP();
        assertTrue(Double.isFinite(expected));
        assertEquals(expected, actual.calculateLogP(), 1e-12);
        assertEquals(
                reference.dataInput.get().getTaxaNames(), actual.dataInput.get().getTaxaNames());
        assertNoSampledState(withMetadata.graph());
    }

    @Test
    void fastaAndNexusProduceTheSameFixedTreeHkyLikelihood() throws Exception {
        Files.writeString(directory.resolve("input.fa"), """
                >a
                ACGTRN-?
                >b
                ACGTACGT
                >c
                ACGTACGA
                """);
        Files.writeString(directory.resolve("input.nex"), nexus("a ACGTRN-?\nb ACGTACGT\nc ACGTACGA", 3, 8));

        Built fasta = build(model("fromFasta", "input.fa"));
        Built nexus = build(model("fromNexus", "input.nex"));
        TreeLikelihood fastaLikelihood = likelihood(fasta);
        TreeLikelihood nexusLikelihood = likelihood(nexus);
        Alignment fastaData = fastaLikelihood.dataInput.get();
        Alignment nexusData = nexusLikelihood.dataInput.get();

        assertEquals(nexusData.getTaxonCount(), fastaData.getTaxonCount());
        assertEquals(nexusData.getSiteCount(), fastaData.getSiteCount());
        assertEquals(nexusData.getTaxaNames(), fastaData.getTaxaNames());
        for (String taxon : fastaData.getTaxaNames()) {
            assertEquals(nexusData.getSequenceAsString(taxon), fastaData.getSequenceAsString(taxon));
        }

        double fastaLogP = fastaLikelihood.calculateLogP();
        double nexusLogP = nexusLikelihood.calculateLogP();
        assertTrue(Double.isFinite(fastaLogP));
        assertTrue(Double.isFinite(nexusLogP));
        assertEquals(nexusLogP, fastaLogP, 1e-10);

        assertNoSampledState(fasta.graph());
        assertNoSampledState(nexus.graph());
    }

    private DecoratedAlignment read(String source) throws Exception {
        Built built = build(source);
        assertEquals(1, built.tiles().size());
        assertNoSampledState(built.graph());
        assertTrue(built.graph().likelihoodDistributions.isEmpty());

        // Tile.apply() returns its cached result for the same index-variable scope.
        return assertInstanceOf(
                DecoratedAlignment.class, built.tiles().getFirst().apply(built.graph(), new IdentityHashMap<>()));
    }

    private Built build(String source) throws Exception {
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

        BEASTState graph = evaluator.applyBestTiling(new BEASTState("fasta-test", directory));
        catalog.configureState(graph);

        for (var distribution : graph.likelihoodDistributions) {
            if (distribution instanceof TreeLikelihood likelihood) {
                likelihood.implementationInput.setValue(TreeLikelihood.class.getName(), likelihood);
            }
        }

        graph.initializeBEASTObjects();
        return new Built(graph, tiles);
    }

    private static void assertNoSampledState(BEASTState graph) {
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static TreeLikelihood likelihood(Built built) {
        assertEquals(1, built.graph().likelihoodDistributions.size());
        return assertInstanceOf(
                TreeLikelihood.class, built.graph().likelihoodDistributions.getFirst());
    }

    private static String metadataSource(String reader, String file, String kind) {
        return """
                Alignment data = %s(file="%s", %s=parse(delimiter="_", part=2))
                """.formatted(reader, file, kind);
    }

    private static String model(String reader, String file) {
        return """
                Alignment data = %s(file="%s")
                Tree tree = fromNewick(newickString="((a:0.1,b:0.1):0.1,c:0.2);")
                QMatrix q = hky(kappa=2.0, baseFrequencies=[0.25,0.25,0.25,0.25])
                Alignment observed ~ PhyloCTMC(tree=tree, qMatrix=q) observed as data
                """.formatted(reader, file);
    }

    private static String nexus(String rows, int taxa, int sites) {
        return """
                #NEXUS
                begin data;
                    dimensions ntax=%d nchar=%d;
                    format datatype=dna missing=? gap=-;
                    matrix
                %s
                    ;
                end;
                """.formatted(taxa, sites, rows);
    }
}
