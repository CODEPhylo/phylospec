import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
        Alignment data = fromNexus(
             "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex"
         )
         Tree tree ~ Yule(
            birthRate=1.0,
            taxa=taxa(data)
         )
         QMatrix qMatrix = jc69()
        
         Vector<Rate> branchRates ~ StrictClock(clockRate=1.0, tree)
         Alignment alignment ~ PhyloCTMC(
           tree, qMatrix, branchRates
         ) observed as data
         Real x ~ Normal(1.0, sd=2.0)
         Real y ~ Normal(mean=x, sd=2.0) observed as 1.0
         PositiveReal a = 1.0 + exp(2 * x + log(5.0))
         Real b = log(a) + 2

        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
