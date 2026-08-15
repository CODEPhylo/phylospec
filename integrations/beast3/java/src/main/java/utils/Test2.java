package utils;

import org.xml.sax.SAXException;
import runner.PhyloSpecRunner;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
           Alignment data = fromNexus("/Users/ochsneto/Documents/PhyloSpec/phylospec/integrations/beast3/java/src/test/java/resources/primate-mtDNA.nex")

           Tree tree ~ Yule(
               birthRate=1.0, taxa=taxa(data)
           )
           
           Rate kappa ~ LogNormal(logMean=1.0, logSd=0.5)

           Alignment alignment ~ PhyloCTMC(
             tree,
             branchRates~StrictClock(clockRate=1.0, tree),
             qMatrix=hky(
               kappa,
               baseFrequencies~Dirichlet(repeat(1.0, num=4))
             ),
           ) observed as data
           
           mcmc {
            Logger logger = treeLogger(
                logEvery=10000, file="hjallo.log", tree
            )
           }
        """;

        PhyloSpecRunner<?, ?> parser = new Beast3Runner(source);
        parser.runPhyloSpec("Test2");
    }

}
