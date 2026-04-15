public class Test2 {

    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus(
            "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex"
        )
        Alignment filtered = subset(
            alignment=data,
            start=10,
            end=898
        )
        
        PositiveReal birthRate ~ LogNormal(mean=1.0, logSd=2.0)
        Tree tree ~ Yule(
            birthRate,
            taxa=taxa(filtered)
        )
        
        PositiveReal kappa ~ LogNormal(mean=1, logSd=1.0)
        Alignment alignment ~ PhyloCTMC(
          tree,
          qMatrix=hky(
            kappa=kappa,
            baseFrequencies=[0.25, 0.25, 0.25, 0.25]
          )
        )
        Alignment a = alignment observed as filtered
        
        Age root = rootAge(tree) observed between [0.01, 3.0]
        
        mcmc {
            Integer chainLength = 10000
        }
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
