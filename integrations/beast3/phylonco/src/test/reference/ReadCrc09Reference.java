import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.MCMC;
import beast.base.parser.XMLParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;

/**
 * Evaluates the LPhyBEAST XML at each fixed state of the CRC09 numerical regression and prints
 * the likelihood and every prior term as properties, named after the PhyloSpec parameters.
 */
class ReadCrc09Reference {
    /** LPhy names that differ from the PhyloSpec names. */
    private static final Map<String, String> NAMES = Map.of(
            "pi", "genotypeFrequencies", "rates", "relativeRates", "psi", "tree", "N", "populationSizes",
            "BayesianSkyline", "tree");

    public static void main(String[] args) throws Exception {
        var states = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(args[1]))) {
            states.load(reader);
        }
        for (String state : states.getProperty("states").split(",")) {
            evaluate(Path.of(args[0]), states, state.trim());
        }
    }

    private static void evaluate(Path xml, Properties states, String state) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = factory.newDocumentBuilder().parse(xml.toFile());
        var nodes = doc.getElementsByTagName("stateNode");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = (Element) nodes.item(i);
            String id = node.getAttribute("id");
            String value = states.getProperty(state + "." + NAMES.getOrDefault(id, id));
            if (value == null) throw new IllegalArgumentException("Missing fixed state for " + state + "." + id);
            if (node.hasAttribute("newick")) {
                node.setAttribute("newick", value);
            } else if (node.hasAttribute("value")) {
                node.setAttribute("value", value);
            } else {
                node.setTextContent(value);
            }
        }
        Path fixed = Files.createTempFile("crc09-fixed-", ".xml");
        try {
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(fixed.toFile()));
            var parsed = (MCMC) new XMLParser().parseFile(fixed.toFile());
            var posterior = (CompoundDistribution) parsed.posteriorInput.get();
            var graph = parsed.startStateInput.get();
            graph.initAndValidate();
            graph.setPosterior(posterior);
            graph.robustlyCalcPosterior(posterior);
            for (Distribution group : posterior.pDistributions.get()) {
                for (Distribution term : ((CompoundDistribution) group).pDistributions.get()) {
                    String name = term.getID().replace(".prior", "");
                    String key = group.getID().equals("likelihood") ? "likelihood" : "prior." + NAMES.getOrDefault(name, name);
                    System.out.printf("%s.%s=%.14f%n", state, key, term.getCurrentLogP());
                }
            }
        } finally {
            Files.deleteIfExists(fixed);
        }
    }
}
