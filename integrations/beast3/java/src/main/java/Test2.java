import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Test2 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        String source = """
                Alignment data[i] = fromNexus("/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex") for i in 1:2
                
                Tree tree[i] ~ Yule(
                    birthRate=1.0, taxa=taxa(data[i])
                ) for i in 1:num(data)
                
                QMatrix qMatrix[i] = hky(
                    kappa~LogNormal(logMean=1.0, logSd=0.5),
                    baseFrequencies~Dirichlet(repeat(1.0, num=4))
                ) for i in 1:num(data)
                
                Vector<Rate> branchRates[i] ~ RelaxedClock(
                    clockRate=10,
                    base=LogNormal(mean=1.0, logSd=2.0),
                    tree=tree[i]
                ) for i in 1:num(data)
                
                Vector<Rate> siteRates[i] ~ DiscreteGammaInv(
                     shape~LogNormal(mean=1.0, logSd=0.05),
                     numCategories=4,
                     numSites=numSites(data[i])
                ) for i in 1:num(data)
                
                Alignment alignment[i] ~ PhyloCTMC(
                    tree=tree[i], qMatrix=qMatrix[1], branchRates=branchRates[i], siteRates=siteRates[i]
                ) observed as data[i] for i in 1:num(data)
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
