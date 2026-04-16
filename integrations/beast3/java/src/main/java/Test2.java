public class Test2 {

    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus(
             "/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex"
         )
         Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
         QMatrix qMatrix = jc69()
        
         Alignment alignment ~ PhyloCTMC(
           tree, qMatrix
         ) observed as data
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
