import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
        Vector<String> files = [
            "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex",
            "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex",
        ]
        Alignment data[i] = fromNexus(files[i]) for i in 1:num(files)
        
        Tree tree ~ Yule(
            birthRate=1.0, taxa=taxa(data[1])
        )
        QMatrix qMatrix = jc69()
        
        Alignment alignment ~ PhyloCTMC(
          tree, qMatrix
        ) observed as data[1]
        
        Alignment alignment ~ PhyloCTMC(
          tree, qMatrix
        ) observed as data[2]
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
