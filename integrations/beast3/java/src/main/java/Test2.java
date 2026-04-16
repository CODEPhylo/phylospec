public class Test2 {

    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus(
            "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex"
        )
        
        Tree tree ~ Yule(
            birthRate=1.0, taxa=taxa(data)
        )
        QMatrix qMatrix = hky(
            kappa~LogNormal(mean=1, logSd=1.0),
            baseFrequencies=[0.25, 0.25, 0.25, 0.25]
        )
        Vector<Rate> branchRates = [2.0]
        Vector<Rate> siteRates = [1.0]
        
        Alignment alignment ~ PhyloCTMC(
          tree, qMatrix, siteRates, branchRates
        ) observed as data
        
        
        mcmc {
            Integer chainLength = 100000
            Logger treeLogger = treeLogger(
                fileName="trees.trees", logEvery=1000
            )
        }
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
