package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;
import runner.PhyloSpecRunner;
import tiles.input.DecoratedAlignment;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

class TaxonSpeciesTileTest {
    private static final List<String> AGE_NAMES = List.of("a_spA_0", "b_spA_1", "c_spB_2");
    private static final List<String> DATE_NAMES = List.of("a_spA_2002", "b_spA_2001", "c_spB_2000");

    @TempDir
    Path directory;

    private record Built(BEASTState graph, List<Tile<?, BEASTState>> tiles) {}

    @Test
    void readsExplicitSpeciesFromManualTaxaWithoutReplacingTheirNames() throws Exception {
        Built built = build("""
                String population = "species A, lineage=1"
                Taxa samples = [taxon(name="sample_1", species=population),
                                taxon(name="sample_2", species=population)]
                String firstSpecies = species(taxon=samples[1])
                String secondSpecies = species(taxon=samples[2])
                String firstName = name(taxon=samples[1])
                String secondName = name(taxon=samples[2])
                NonNegativeInteger count = num(vector=samples)
                """);
        assertEquals("species A, lineage=1", value(built, "firstSpecies"));
        assertEquals("species A, lineage=1", value(built, "secondSpecies"));
        assertEquals("sample_1", value(built, "firstName"));
        assertEquals("sample_2", value(built, "secondName"));
        assertEquals(2, assertInstanceOf(IntScalar.class, value(built, "count")).get());
        assertTrue(built.graph().stateNodes.isEmpty());
        assertTrue(built.graph().operators.isEmpty());
    }

    @Test
    void missingManualSpeciesIsAnErrorRatherThanATaxonNameFallback() {
        assertThrows(TileApplicationError.class, () -> build("""
                Taxon sample = taxon(name="sample_1")
                String population = species(taxon=sample)
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsBlankExplicitSpecies(String species) {
        assertThrows(
                TileApplicationError.class,
                () -> build("Taxon sample = taxon(name=\"a\", species=\"" + species + "\")"));
    }

    private static Stream<Arguments> invalidSpeciesParsers() {
        return Stream.of("fromFasta", "fromNexus").flatMap(reader -> Stream.of(
                        "parse(delimiter=\"_\", part=99)",
                        "parse(regex=\"(Z+)\")",
                        "parse(regex=\"^[^_]+_\")",
                        "parse(regex=\"^[^_]+_()\")")
                .map(parser -> Arguments.of(reader, parser)));
    }

    @ParameterizedTest
    @MethodSource("invalidSpeciesParsers")
    void invalidSpeciesExtractionReportsTheFileAndTaxon(String reader, String parser) throws Exception {
        String file = writeInput(reader, AGE_NAMES);
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> build("Alignment data = " + reader + "(file=\"" + file + "\", speciesName=" + parser + ")"));
        assertTrue(error.getMessage().contains(file), error.getMessage());
        assertTrue(error.getMessage().contains(AGE_NAMES.getFirst()), error.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"fromFasta,delimiter", "fromFasta,regex", "fromNexus,delimiter", "fromNexus,regex"})
    void bothReadersAndParsersPreserveSpeciesAndDistinctOrderedSamples(String reader, String parser) throws Exception {
        String file = writeInput(reader, AGE_NAMES);
        String expression =
                parser.equals("delimiter") ? "parse(delimiter=\"_\", part=2)" : "parse(regex=\"^[^_]+_([^_]+)_\")";
        String source = "Alignment data = " + reader + "(file=\"" + file + "\", speciesName=" + expression + ")\n";
        DecoratedAlignment data = assertInstanceOf(DecoratedAlignment.class, value(build(source), "data"));
        assertEquals(AGE_NAMES, data.alignment().getTaxaNames());
        assertEquals(AGE_NAMES, data.taxonSet().asStringList());
        assertEquals(3, data.alignment().getTaxonCount());
        assertEquals(
                Map.of(AGE_NAMES.get(0), "spA", AGE_NAMES.get(1), "spA", AGE_NAMES.get(2), "spB"), data.speciesNames());
        assertNull(data.ages());
        assertThrows(
                UnsupportedOperationException.class, () -> data.speciesNames().put(AGE_NAMES.getFirst(), "other"));

        Built taxa = build(source + "Taxa samples = taxa(alignment=data)");
        TaxaData samples = assertInstanceOf(TaxaData.class, value(taxa, "samples"));
        assertEquals(AGE_NAMES, samples.stream().map(TaxonValue::name).toList());
        assertEquals(
                List.of("spA", "spA", "spB"),
                samples.stream().map(TaxonValue::species).toList());
        assertEquals(
                List.of(0.0, 0.0, 0.0), samples.stream().map(TaxonValue::age).toList());
        assertSame(samples, TaxaData.fromList(taxa.graph(), samples));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fromFasta", "fromNexus"})
    void computedParserPartExtractsSpeciesFromBothInputFormats(String reader) throws Exception {
        Files.writeString(directory.resolve("reference.fa"), ">reference_a\nAC\n>reference_b\nAG\n");
        String file = writeInput(reader, AGE_NAMES);
        Built built = build("""
                Alignment reference = fromFasta(file="reference.fa")
                PositiveInteger field = numSites(alignment=reference)
                Alignment data = %s(file="%s", speciesName=parse(delimiter="_", part=field))
                Taxa samples = taxa(alignment=data)
                """.formatted(reader, file));
        TaxaData samples = assertInstanceOf(TaxaData.class, value(built, "samples"));
        assertEquals(AGE_NAMES, samples.stream().map(TaxonValue::name).toList());
        assertEquals(
                List.of("spA", "spA", "spB"),
                samples.stream().map(TaxonValue::species).toList());
        assertEquals(
                List.of(0.0, 0.0, 0.0), samples.stream().map(TaxonValue::age).toList());
        assertTrue(built.graph().stateNodes.isEmpty());
        assertTrue(built.graph().operators.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"fromFasta,age", "fromFasta,date", "fromNexus,age", "fromNexus,date"})
    void samplingMetadataAndSpeciesSurviveSubsetAndIndexedAccess(String reader, String kind) throws Exception {
        List<String> names = kind.equals("date") ? DATE_NAMES : AGE_NAMES;
        String file = writeInput(reader, names);
        String source = """
                Alignment data = %s(file="%s", speciesName=parse(delimiter="_", part=2),
                                   %s=parse(delimiter="_", part=3))
                Alignment original = data
                Alignment filtered = subset(alignment=data, start=2, end=3)
                """.formatted(reader, file, kind);
        Built subset = build(source);
        DecoratedAlignment original = assertInstanceOf(DecoratedAlignment.class, value(subset, "original"));
        DecoratedAlignment filtered = assertInstanceOf(DecoratedAlignment.class, value(subset, "filtered"));
        assertSame(original.taxonSet(), filtered.taxonSet());
        assertSame(original.ages(), filtered.ages());
        assertEquals(original.speciesNames(), filtered.speciesNames());
        assertEquals(2, filtered.alignment().getSiteCount());
        assertEquals(names, filtered.alignment().getTaxaNames());
        assertEquals("CG", filtered.alignment().getSequenceAsString(names.getFirst()));
        assertEquals(Map.of(names.get(0), "spA", names.get(1), "spA", names.get(2), "spB"), filtered.speciesNames());
        assertEquals(kind, filtered.ages().getTraitName());
        assertSame(filtered.taxonSet(), filtered.ages().taxaInput.get());
        for (int i = 0; i < names.size(); i++) {
            assertEquals(i, filtered.ages().getValue(names.get(i)), 1e-12);
        }

        String taxaSource = source + "Taxa samples = taxa(alignment=filtered)\n";
        // These assertions check separate metadata paths. Keep the scripts small so
        // unrelated outputs do not multiply the tiler's cross-statement candidates.
        Built species = build(taxaSource + """
                String firstSpecies = species(taxon=samples[1])
                String secondSpecies = species(taxon=samples[2])
                String thirdSpecies = species(taxon=samples[3])
                """);
        assertEquals("spA", value(species, "firstSpecies"));
        assertEquals("spA", value(species, "secondSpecies"));
        assertEquals("spB", value(species, "thirdSpecies"));

        Built metadata = build(taxaSource + """
                String secondName = name(taxon=samples[2])
                NonNegativeReal secondAge = age(taxon=samples[2])
                NonNegativeInteger count = num(vector=samples)
                """);
        assertEquals(names.get(1), value(metadata, "secondName"));
        assertEquals(
                1.0,
                assertInstanceOf(RealScalar.class, value(metadata, "secondAge")).get(),
                1e-12);
        assertEquals(
                3, assertInstanceOf(IntScalar.class, value(metadata, "count")).get());

        Built built = build(taxaSource + "Tree inferred ~ Coalescent(populationSize=100.0, taxa=samples)\n");
        assertEquals(1, built.graph().stateNodes.size());
        Tree tree = assertInstanceOf(
                Tree.class, built.graph().stateNodes.keySet().iterator().next());
        assertEquals(
                Map.of(names.get(0), 0.0, names.get(1), 1.0, names.get(2), 2.0),
                tree.getExternalNodes().stream().collect(Collectors.toMap(Node::getID, Node::getHeight)));
        assertTrue(Double.isFinite(built.graph().priorDistributions.get(tree).calculateLogP()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fromFasta", "fromNexus"})
    void readersWithoutSpeciesMetadataDoNotInferSpeciesFromNames(String reader) throws Exception {
        String file = writeInput(reader, AGE_NAMES);
        String source = "Alignment data = " + reader + "(file=\"" + file + "\")\n";
        DecoratedAlignment data = assertInstanceOf(DecoratedAlignment.class, value(build(source), "data"));
        assertTrue(data.speciesNames().isEmpty());
        assertThrows(TileApplicationError.class, () -> build(source + """
                Taxa samples = taxa(alignment=data)
                String population = species(taxon=samples[1])
                """));
    }

    @Test
    void managedRunnerUsesSpeciesAndAgeMetadataWithoutCollapsingSamples() throws Exception {
        String file = writeInput("fromFasta", AGE_NAMES);
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, """
                Alignment data = fromFasta(file="%s", speciesName=parse(delimiter="_", part=2),
                                           age=parse(delimiter="_", part=3))
                Taxa samples = taxa(alignment=data)
                String population = species(taxon=samples[2])
                Tree tree ~ Coalescent(populationSize=100.0, taxa=samples)
                Alignment observed ~ PhyloCTMC(tree=tree, qMatrix=jc69()) observed as data
                mcmc {
                    Integer chainLength = 10
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 42
                }
                """.formatted(file));
        PhyloSpecRunner runner = new PhyloSpecRunner(source, List.of("beast2"));
        assertNotNull(runner.check());
        Path run = directory.resolve("run");
        runner.runManaged("species", run, warning -> {});
        var record = new ObjectMapper().readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertEquals(1, record.path("inputs").size());
        assertEquals(
                directory.resolve(file).toString(),
                record.path("inputs").get(0).path("path").asText());
        List<String> trace = Files.readAllLines(run.resolve("output/species.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(12, trace.size(), "Header and samples 0 through 10");
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        for (int i = 1; i < trace.size(); i++) {
            String[] values = trace.get(i).trim().split("\\s+");
            assertEquals(i - 1, Integer.parseInt(values[0]));
            for (String name : List.of("posterior", "prior", "likelihood")) {
                assertTrue(columns.contains(name), columns.toString());
                assertTrue(Double.isFinite(Double.parseDouble(values[columns.indexOf(name)])));
            }
        }
        String trees = Files.readString(run.resolve("output/species.trees"));
        assertTrue(trees.contains("STATE_10"));
        for (String name : AGE_NAMES) assertTrue(trees.contains(name), name);
        assertTrue(Files.size(run.resolve("output/species.state.xml")) > 0);
    }

    private String writeInput(String reader, List<String> names) throws Exception {
        boolean fasta = reader.equals("fromFasta");
        String file = fasta ? "input.fa" : "input.nex";
        String rows = names.get(0) + " ACGT\n" + names.get(1) + " ACGA\n" + names.get(2) + " ACGG\n";
        Files.writeString(
                directory.resolve(file),
                fasta
                        ? ">" + names.get(0) + "\nACGT\n>" + names.get(1) + "\nACGA\n>" + names.get(2) + "\nACGG\n"
                        : """
                        #NEXUS
                        begin data;
                            dimensions ntax=3 nchar=4;
                            format datatype=dna missing=? gap=-;
                            matrix
                        %s
                            ;
                        end;
                        """.formatted(rows));
        return file;
    }

    private Built build(String source) throws Exception {
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("species", directory));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return new Built(graph, tiles);
    }

    private static Object value(Built built, String name) {
        return built.tiles().stream()
                .filter(tile -> tile.getRootNode() instanceof Stmt stmt
                        && stmt.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing output " + name))
                .apply(built.graph(), new IdentityHashMap<>());
    }
}
