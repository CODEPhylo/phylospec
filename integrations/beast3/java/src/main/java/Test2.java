public class Test2 {

    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus("/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex")
        
        Tree tree ~ Yule(
            birthRate~LogNormal(logMean=1.0, logSd=2.0),
            taxa=taxa(data)
        )
        
        Alignment alignment ~ PhyloCTMC(
          tree,
          branchRates~RelaxedClock(
            base=LogNormal(mean=1.0, logSd=2.0),
            clockRate~LogNormal(logMean=1.0, logSd=2.0),
            tree=tree
          ),
          qMatrix=jc69(),
          siteRates~DiscreteGammaInv(
            shape~LogNormal(logMean=1.0, logSd=2.0),
            numCategories=4,
            numSites=100
          )
        ) observed as data
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec();
    }

}
