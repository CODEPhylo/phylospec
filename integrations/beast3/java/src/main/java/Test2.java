import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
        Vector<String> filePaths = [
                "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex",
                "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex",
        ]
        Alignment data[i] = fromNexus(filePaths[i]) for i in 1:num(filePaths)
        
        Tree tree ~ Yule(
            birthRate=1.0, taxa=taxa(data[1])
        )
        QMatrix qMatrix = jc69()
        
        Alignment alignment[i] ~ PhyloCTMC(
          tree, qMatrix
        ) observed as data[i] for i in 1:num(filePaths)
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
