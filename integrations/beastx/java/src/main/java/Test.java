import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
                Alignment data = fromNexus(
                   "/Users/ochsneto/Documents/PhyloSpec/phylospec/integrations/beast3/java/src/test/java/resources/primate-mtDNA.nex"
               )
    
               Tree tree ~ Yule(
                   birthRate~LogNormal(mean=0.2, logSd=1.0),
                   taxa=taxa(data)
               )
               QMatrix qMatrix = jc69()
    
               Alignment alignment ~ PhyloCTMC(
                   tree, qMatrix
               ) observed as data
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test");
    }

}
