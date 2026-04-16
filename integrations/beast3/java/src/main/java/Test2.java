public class Test2 {

    static void main(String[] args) {
        String source = """
        Real x ~ Normal(mean=1.0, sd=2.0)
        Real y ~ Normal(mean=1.0, sd=2.0)
        Real z = -2.0
        Real w = -x + (exp(z) - x * y) / y
        Real k = 2*w
        """;

        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec("Test2");
    }

}
