import dr.inference.model.Likelihood;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.model.BeastXBMTraitLikelihoodSpec;
import tiling.model.BeastXOUTraitLikelihoodSpec;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXContinuousTraitModelsTest {

    private void assertNonEmptyFile(Path path) throws Exception {
        assertTrue(
                Files.exists(path),
                "Expected file to exist: " + path
        );

        assertTrue(
                Files.size(path) > 0,
                "Expected file to be non-empty: " + path
        );
    }

    @Test
    public void phyloBMStrictClockLikelihoodIsBuiltAndEvaluates() throws Exception {
        String source = """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
                Taxa taxa = taxa(molecularData)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                Rate diffusionRate ~ LogNormal(logMean=0.0, logSd=1.0)

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=diffusionRate,
                    tree=tree
                )

                Vector<Rate> siteRates = [1.0]
                Vector<Real> rootValues = [0.0]

                Alignment traits ~ PhyloBM(
                    tree=tree,
                    branchRates=branchRates,
                    siteRates=siteRates,
                    rootValues=rootValues
                ) observed as traitData
                """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("continuousTraitBM");

        assertEquals(1, model.beastState.likelihoodDistributions.size());

        Likelihood likelihood =
                model.beastState.likelihoodDistributions.getFirst();

        assertInstanceOf(BeastXBMTraitLikelihoodSpec.class, likelihood);
        assertEquals("traits_likelihood", likelihood.getId());

        assertTrue(Double.isFinite(model.likelihood.getLogLikelihood()));
        assertTrue(Double.isFinite(model.posterior.getLogLikelihood()));
    }

    @Test
    public void phyloBMRelaxedClockLikelihoodIsBuiltAndEvaluates() throws Exception {
        String source = """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
                Taxa taxa = taxa(molecularData)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Rate diffusionRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Vector<Rate> branchRates ~ RelaxedClock(
                    clockRate=diffusionRate,
                    base=LogNormal(mean=1.0, logSd=0.1),
                    tree=tree
                )

                Vector<Rate> siteRates = [1.0]
                Vector<Real> rootValues = [0.0]

                Alignment traits ~ PhyloBM(
                    tree=tree,
                    branchRates=branchRates,
                    siteRates=siteRates,
                    rootValues=rootValues
                ) observed as traitData
                """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("continuousTraitBMRelaxedClock");

        assertEquals(1, model.beastState.likelihoodDistributions.size());

        Likelihood likelihood =
                model.beastState.likelihoodDistributions.getFirst();

        assertInstanceOf(BeastXBMTraitLikelihoodSpec.class, likelihood);
        assertEquals("traits_likelihood", likelihood.getId());

        assertTrue(model.beastState.stateNodesByPhyloSpecName.containsKey("diffusionRate"));
        assertTrue(
                model.beastState.treeRelaxedClockModels.containsKey(
                        model.beastState.treeModelsByPhyloSpecName.get("tree")
                )
        );

        assertTrue(Double.isFinite(model.likelihood.getLogLikelihood()));
        assertTrue(Double.isFinite(model.posterior.getLogLikelihood()));
    }

    @Test
    public void phyloOULikelihoodIsBuiltAndEvaluates() throws Exception {
        String source = """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
                Taxa taxa = taxa(molecularData)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)

                Vector<PositiveReal> siteVariances = [1.0]
                PositiveReal selectionStrength ~ LogNormal(logMean=0.0, logSd=1.0)
                Vector<Real> siteOptima = [0.0]
                Vector<Real> rootValues = [0.0]

                Alignment traits ~ PhyloOU(
                    tree=tree,
                    siteVariances=siteVariances,
                    selectionStrength=selectionStrength,
                    siteOptima=siteOptima,
                    rootValues=rootValues
                ) observed as traitData
                """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("continuousTraitOU");

        assertEquals(1, model.beastState.likelihoodDistributions.size());

        Likelihood likelihood =
                model.beastState.likelihoodDistributions.getFirst();

        assertInstanceOf(BeastXOUTraitLikelihoodSpec.class, likelihood);
        assertEquals("traits_likelihood", likelihood.getId());

        assertTrue(Double.isFinite(model.likelihood.getLogLikelihood()));
        assertTrue(Double.isFinite(model.posterior.getLogLikelihood()));
    }

    @Test
    public void phyloBMStrictClockMCMCRunsAndWritesLog() throws Exception {
        Path outputDirectory =
                Path.of("target", "continuous-trait-mcmc");

        Files.createDirectories(outputDirectory);

        Path logPath =
                outputDirectory.resolve("phyloBMStrictClock-" + System.nanoTime() + ".log");

        Files.deleteIfExists(logPath);

        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa taxa = taxa(molecularData)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=taxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
            Rate diffusionRate ~ LogNormal(logMean=0.0, logSd=1.0)

            Vector<Rate> branchRates ~ StrictClock(
                clockRate=diffusionRate,
                tree=tree
            )

            Vector<Rate> siteRates = [1.0]
            Vector<Real> rootValues = [0.0]

            Alignment traits ~ PhyloBM(
                tree=tree,
                branchRates=branchRates,
                siteRates=siteRates,
                rootValues=rootValues
            ) observed as traitData

            mcmc {
                Integer chainLength = 20
                Integer randomSeed = 1234

                Logger fileLogger = fileLogger(
                    logEvery=5,
                    file="%s",
                    parameters=[diffusionRate]
                )
            }
            """.formatted(logPath.toString().replace("\\", "/"));

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("phyloBMStrictClockMCMC")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(20)
                                        .build()
                        );

        assertTrue(result.hasModel());
        assertTrue(result.hasMCMC());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected BM MCMC log file to exist.");
        assertTrue(Files.size(logPath) > 0, "Expected BM MCMC log file to be non-empty.");

        try (Stream<String> lines = Files.lines(logPath)) {
            assertTrue(
                    lines.count() >= 2,
                    "Expected BM MCMC log to contain a header and at least one sample."
            );
        }
    }

    @Test
    public void phyloOUMCMCRunsAndWritesLog() throws Exception {
        Path outputDirectory =
                Path.of("target", "continuous-trait-mcmc");

        Files.createDirectories(outputDirectory);

        Path logPath =
                outputDirectory.resolve("phyloOU-" + System.nanoTime() + ".log");

        Files.deleteIfExists(logPath);

        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa taxa = taxa(molecularData)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=taxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)

            Vector<PositiveReal> siteVariances = [1.0]
            PositiveReal selectionStrength ~ LogNormal(logMean=0.0, logSd=1.0)
            Vector<Real> siteOptima = [0.0]
            Vector<Real> rootValues = [0.0]

            Alignment traits ~ PhyloOU(
                tree=tree,
                siteVariances=siteVariances,
                selectionStrength=selectionStrength,
                siteOptima=siteOptima,
                rootValues=rootValues
            ) observed as traitData

            mcmc {
                Integer chainLength = 20
                Integer randomSeed = 5678

                Logger fileLogger = fileLogger(
                    logEvery=5,
                    file="%s",
                    parameters=[selectionStrength]
                )
            }
            """.formatted(logPath.toString().replace("\\", "/"));

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("phyloOUMCMC")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(20)
                                        .build()
                        );

        assertTrue(result.hasModel());
        assertTrue(result.hasMCMC());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected OU MCMC log file to exist.");
        assertTrue(Files.size(logPath) > 0, "Expected OU MCMC log file to be non-empty.");

        try (Stream<String> lines = Files.lines(logPath)) {
            assertTrue(
                    lines.count() >= 2,
                    "Expected OU MCMC log to contain a header and at least one sample."
            );
        }
    }

    @Test
    public void phyloBMStrictClockMCMCRunsAndWritesParameterAndTreeLogs() throws Exception {
        Path outputDirectory =
                Path.of("target", "continuous-trait-mcmc");

        Files.createDirectories(outputDirectory);

        Path logPath =
                outputDirectory.resolve("phyloBMStrictClock-" + System.nanoTime() + ".log");

        Path treeLogPath =
                outputDirectory.resolve("phyloBMStrictClock-" + System.nanoTime() + ".trees");

        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa taxa = taxa(molecularData)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=taxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
            Rate diffusionRate ~ LogNormal(logMean=0.0, logSd=1.0)

            Vector<Rate> branchRates ~ StrictClock(
                clockRate=diffusionRate,
                tree=tree
            )

            Vector<Rate> siteRates = [1.0]
            Vector<Real> rootValues = [0.0]

            Alignment traits ~ PhyloBM(
                tree=tree,
                branchRates=branchRates,
                siteRates=siteRates,
                rootValues=rootValues
            ) observed as traitData

            mcmc {
                Integer chainLength = 20
                Integer randomSeed = 1234

                Logger fileLogger = fileLogger(
                    logEvery=5,
                    file="%s",
                    parameters=[diffusionRate]
                )

                Logger treeLogger = treeLogger(
                    logEvery=5,
                    file="%s",
                    trees=[tree]
                )
            }
            """.formatted(
                logPath.toString().replace("\\", "/"),
                treeLogPath.toString().replace("\\", "/")
        );

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("phyloBMStrictClockMCMCTreeLog")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(20)
                                        .build()
                        );

        assertTrue(result.hasModel());
        assertTrue(result.hasMCMC());
        assertTrue(result.executed());

        assertNonEmptyFile(logPath);
        assertNonEmptyFile(treeLogPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(
                treeLog.contains("tree STATE_"),
                "Expected tree log to contain sampled STATE trees."
        );
    }

    @Test
    public void phyloOUMCMCRunsAndWritesParameterAndTreeLogs() throws Exception {
        Path outputDirectory =
                Path.of("target", "continuous-trait-mcmc");

        Files.createDirectories(outputDirectory);

        Path logPath =
                outputDirectory.resolve("phyloOU-" + System.nanoTime() + ".log");

        Path treeLogPath =
                outputDirectory.resolve("phyloOU-" + System.nanoTime() + ".trees");

        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa taxa = taxa(molecularData)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=taxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)

            Vector<PositiveReal> siteVariances = [1.0]
            PositiveReal selectionStrength ~ LogNormal(logMean=0.0, logSd=1.0)
            Vector<Real> siteOptima = [0.0]
            Vector<Real> rootValues = [0.0]

            Alignment traits ~ PhyloOU(
                tree=tree,
                siteVariances=siteVariances,
                selectionStrength=selectionStrength,
                siteOptima=siteOptima,
                rootValues=rootValues
            ) observed as traitData

            mcmc {
                Integer chainLength = 20
                Integer randomSeed = 5678

                Logger fileLogger = fileLogger(
                    logEvery=5,
                    file="%s",
                    parameters=[selectionStrength]
                )

                Logger treeLogger = treeLogger(
                    logEvery=5,
                    file="%s",
                    trees=[tree]
                )
            }
            """.formatted(
                logPath.toString().replace("\\", "/"),
                treeLogPath.toString().replace("\\", "/")
        );

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("phyloOUMCMCTreeLog")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(20)
                                        .build()
                        );

        assertTrue(result.hasModel());
        assertTrue(result.hasMCMC());
        assertTrue(result.executed());

        assertNonEmptyFile(logPath);
        assertNonEmptyFile(treeLogPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(
                treeLog.contains("tree STATE_"),
                "Expected tree log to contain sampled STATE trees."
        );
    }

    @Test
    public void phyloBMRejectsObservedTraitTaxonMissingFromTree() {
        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa treeTaxa = taxa(molecularData)

            Alignment traitSource = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa traitTaxa = taxa(traitSource)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=traitTaxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=treeTaxa
            )

            Vector<Rate> branchRates ~ StrictClock(
                clockRate=1.0,
                tree=tree
            )

            Vector<Rate> siteRates = [1.0]

            Alignment traits ~ PhyloBM(
                tree=tree,
                branchRates=branchRates,
                siteRates=siteRates
            ) observed as traitData
            """;

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> new PhyloSpecRunner(source).buildModel("phyloBMMissingTraitTaxon")
                );

        assertTrue(
                exception.getMessage().contains("PhyloBM")
                        && exception.getMessage().contains("not present as a tree tip"),
                exception.getMessage()
        );
    }

    @Test
    public void phyloBMMCMCSamplesEstimatedSiteRateAndRootValue() throws Exception {
        Path outputDirectory =
                Path.of("target", "continuous-trait-mcmc", "bm-estimated-" + System.nanoTime());

        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa taxa = taxa(molecularData)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=taxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )

            Vector<Rate> branchRates ~ StrictClock(
                clockRate=1.0,
                tree=tree
            )

            PositiveReal diffusionRate ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )

            Real rootValue ~ Normal(
                mean=0.0,
                sd=1.0
            )

            Vector<Rate> siteRates = [diffusionRate]
            Vector<Real> rootValues = [rootValue]

            Alignment traits ~ PhyloBM(
                tree=tree,
                branchRates=branchRates,
                siteRates=siteRates,
                rootValues=rootValues
            ) observed as traitData
            """;

        RunnerOptions options =
                RunnerOptions.builder("phyloBMEstimatedParameters")
                        .mode(RunMode.EXECUTE_MCMC)
                        .chainLengthOverride(20)
                        .defaultLogEveryOverride(5)
                        .outputPrefix(outputDirectory, "bm-estimated")
                        .build();

        BeastXRunResult result =
                new PhyloSpecRunner(source).run(options);

        Path logPath =
                outputDirectory.resolve("bm-estimated.log");

        assertTrue(result.hasMCMC());
        assertTrue(result.executed());
        assertNonEmptyFile(logPath);

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("diffusionRate"), log);
        assertTrue(log.contains("rootValue"), log);
    }

    @Test
    public void phyloOUMCMCSamplesEstimatedSelectionOptimumVarianceAndRootValue() throws Exception {
        Path outputDirectory =
                Path.of("target", "continuous-trait-mcmc", "ou-estimated-" + System.nanoTime());

        String source = """
            Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
            Taxa taxa = taxa(molecularData)

            Alignment traitData = continuousTraitsFromTaxa(
                taxa=taxa,
                trait=parse(regex=".*_([01])$")
            )

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )

            PositiveReal siteVariance ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )

            PositiveReal alpha ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )

            Real optimum ~ Normal(
                mean=0.0,
                sd=1.0
            )

            Real rootValue ~ Normal(
                mean=0.0,
                sd=1.0
            )

            Vector<PositiveReal> siteVariances = [siteVariance]
            Vector<Real> siteOptima = [optimum]
            Vector<Real> rootValues = [rootValue]

            Alignment traits ~ PhyloOU(
                tree=tree,
                siteVariances=siteVariances,
                selectionStrength=alpha,
                siteOptima=siteOptima,
                rootValues=rootValues
            ) observed as traitData
            """;

        RunnerOptions options =
                RunnerOptions.builder("phyloOUEstimatedParameters")
                        .mode(RunMode.EXECUTE_MCMC)
                        .chainLengthOverride(20)
                        .defaultLogEveryOverride(5)
                        .outputPrefix(outputDirectory, "ou-estimated")
                        .build();

        BeastXRunResult result =
                new PhyloSpecRunner(source).run(options);

        Path logPath =
                outputDirectory.resolve("ou-estimated.log");

        assertTrue(result.hasMCMC());
        assertTrue(result.executed());
        assertNonEmptyFile(logPath);

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("siteVariance"), log);
        assertTrue(log.contains("alpha"), log);
        assertTrue(log.contains("optimum"), log);
        assertTrue(log.contains("rootValue"), log);
    }
}