public class Test2 {

    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus(
            "/Users/ochsneto/Documents/PhyloSpec/phylospec/integrations/beast3/java/src/test/java/resources/bdmm.nex",
            age=parse(delimiter="_", part=4)
        )
        
        Tree initial = fromTree("/Users/ochsneto/Documents/BDMM/BDMM-Flow-Supplementary/beast_benchmarks/H3N2/results/bdmm-flow.15.trees")
        
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
