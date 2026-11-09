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

/** Evaluate the LPhyBEAST XML with the state used by the CRC09 numerical regression. */
class ReadCrc09Reference {
    public static void main(String[] args) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = factory.newDocumentBuilder().parse(Path.of(args[0]).toFile());
        var state = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(args[1]))) {
            state.load(reader);
        }
        var names = Map.of("pi", "genotypeFrequencies", "rates", "relativeRates",
                "psi", "tree", "N", "populationSizes");
        var nodes = doc.getElementsByTagName("stateNode");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = (Element) nodes.item(i);
            String id = node.getAttribute("id");
            String value = state.getProperty(names.getOrDefault(id, id));
            if (value == null) throw new IllegalArgumentException("Missing fixed state for " + id);
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
            for (Distribution d : posterior.pDistributions.get()) {
                System.out.printf("%s = %.14f%n", d.getID(), d.getCurrentLogP());
                if (d instanceof CompoundDistribution group) {
                    for (var term : group.pDistributions.get()) {
                        System.out.printf("  %s = %.14f%n", term.getID(), term.getCurrentLogP());
                    }
                }
            }
        } finally {
            Files.deleteIfExists(fixed);
        }
    }
}
