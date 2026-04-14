public class Test2 {

    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus(
            "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex"
        )
        Alignment filtered = subset(
            alignment=data,
            start=100,
            end=200
        )
        
        Tree tree = fromNewick("(df")
        
        Alignment alignment ~ PhyloCTMC(
          tree,
          qMatrix=hky(
            kappa=1.5,
            baseFrequencies=[0.25, 0.25, 0.25, 0.25]
          ),
          siteRates~DiscreteGammaInv(
            shape~LogNormal(logMean=1.0, logSd=2.0),
            numCategories=4,
            numSites=100
          )
        ) observed as filtered
        
        Age root = rootAge(tree) observed between [0.01, 3.0]
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec();
    }

}
