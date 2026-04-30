import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
              Tree data = fromNewick("file.newick")
              Rate birthRate ~ LogNormal(mean=1.0, logSd=2.0)
              Tree tree ~ Yule(birthRate, taxa=taxa(data)) observed as data
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
