import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
                Alignment data = fromNexus("/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex")

                Tree tree ~ Yule(
                    birthRate=1.0, taxa=taxa(data)
                )
                
                Vector<PositiveReal> logMeans = [1.0, 0.5]
                
                QMatrix qMatrix[i] = hky(
                 kappa~LogNormal(logMean=logMeans[i], logSd=0.5),
                 baseFrequencies~Dirichlet(repeat(1.0, num=4))
                ) for i in 1:2
                
                Alignment alignment[i] ~ PhyloCTMC(
                  tree, qMatrix=qMatrix[i]
                ) observed as data for i in 1:2
                
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
