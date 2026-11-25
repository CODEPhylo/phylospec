import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the exact trace columns BEAST X writes, in memory and from the saved XML. They follow the
 * BEAST 3 names: the posterior is called posterior, and the columns of a vector are numbered as
 * name.1, name.2, ...
 */
class BeastXTraceColumnsTest {

    private static final String TREE_DATA = """
            Tree treeData = fromNewick("((A:1.0,B:1.0):1.0,(C:1.0,D:1.0):1.0);")
            Simplex pi ~ Dirichlet(concentration=repeat(value=1.0, num=3))
            """;

    @ParameterizedTest(name = "viaXml={0}")
    @ValueSource(booleans = {false, true})
    void namesThePosteriorAndNumbersVectorsLikeBeast3(boolean viaXml) throws Exception {
        String name = "traceColumnsPosterior" + (viaXml ? "Xml" : "InMemory");
        Path plain = XmlTestSupport.logPath(name);
        Path renamed = XmlTestSupport.logPath(name + "Renamed");
        String source = TREE_DATA + """
                PositiveReal birthRate ~ LogNormal(logMean=0.0, logSd=1.0)
                Tree tree ~ Yule(birthRate=birthRate, taxa=taxa(treeData)) observed as treeData

                mcmc {
                    Sampler sampler = mcmc(chainLength=10)
                    Logger plain = fileLogger(file="%s", logEvery=1, parameters=[pi])
                    Logger renamed = fileLogger(
                        file="%s", logEvery=1, parameters=[birthRate, pi], parameterNames=["rate", "freq"]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(plain), XmlTestSupport.unixPath(renamed));

        run(name, source, viaXml, plain, renamed);

        assertTrace(plain, List.of("state", "posterior", "prior", "likelihood", "pi.1", "pi.2", "pi.3"));
        assertTrace(
                renamed,
                List.of("state", "posterior", "prior", "likelihood", "rate", "freq.1", "freq.2", "freq.3"));
    }

    @ParameterizedTest(name = "viaXml={0}")
    @ValueSource(booleans = {false, true})
    void numbersRenamedTreesLikeVectors(boolean viaXml) throws Exception {
        String name = "traceColumnsTree" + (viaXml ? "Xml" : "InMemory");
        Path log = XmlTestSupport.logPath(name);
        String source = TREE_DATA + """
                Tree observed ~ Yule(birthRate=1.0, taxa=taxa(treeData)) observed as treeData
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(treeData))

                mcmc {
                    Sampler sampler = mcmc(chainLength=10)
                    Logger log = fileLogger(
                        file="%s", logEvery=1, parameters=[tree, pi], parameterNames=["myTree", "freq"]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(log));

        run(name, source, viaXml, log);

        assertTrace(
                log,
                List.of(
                        "state",
                        "posterior",
                        "prior",
                        "likelihood",
                        "myTree.1",
                        "myTree.2",
                        "freq.1",
                        "freq.2",
                        "freq.3"));
    }

    private static void run(String name, String source, boolean viaXml, Path... logs) throws Exception {
        XmlTestSupport.prepare(logs);
        if (viaXml) {
            Path xml = XmlTestSupport.xmlPath(name);
            XmlTestSupport.prepare(xml);
            XmlTestSupport.buildWriteAndRunXml(name, source, xml);
        } else {
            PhyloSpecRunner runner = new PhyloSpecRunner(source);
            runner.setSeed(1L);
            runner.runMCMC(name);
        }
    }

    /** Checks the exact header and that every sample has one value per column. */
    private static void assertTrace(Path log, List<String> header) throws Exception {
        List<String> lines = Files.readAllLines(log).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        assertEquals(header, List.of(lines.getFirst().split("\\s+")), log.toString());
        assertEquals(12, lines.size(), "Header and samples 0 through 10 in " + log);
        for (String sample : lines.subList(1, lines.size())) {
            assertEquals(header.size(), sample.split("\\s+").length, sample);
        }
    }
}
