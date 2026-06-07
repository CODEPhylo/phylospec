import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.NodeHeightScaleOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.WilsonBalding;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.ScaleOperator;
import dr.inference.operators.UpDownOperator;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.operators.OperatorBuilder;
import tiling.BeastXState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXOperatorBuilderTest {

    @Test
    public void buildsScaleOperatorForPositiveRealParameter() throws Exception {
        String source =
                """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, ScaleOperator.class));
    }

    @Test
    public void buildsRandomWalkOperatorForRealParameter() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=0.0, sd=1.0)
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, RandomWalkOperator.class));
    }

    @Test
    public void buildsDeltaExchangeOperatorForSimplexParameter() throws Exception {
        String source =
                """
                Simplex frequencies ~ Dirichlet(concentration=[1.0, 1.0, 1.0, 1.0])
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, DeltaExchangeOperator.class));
    }

    @Test
    public void buildsDefaultTreeOperatorsForStochasticTree() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(5, operators.size());
        assertTrue(containsOperator(operators, NodeHeightScaleOperator.class));
        assertTrue(containsOperator(operators, SubtreeSlideOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
    }

    @Test
    public void buildsParameterAndTreeOperatorsForPhylogeneticModel() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                PositiveReal kappa ~ LogNormal(logMean=0.0, logSd=1.0)
                Simplex baseFrequencies ~ Dirichlet(concentration=[1.0, 1.0, 1.0, 1.0])

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                QMatrix qMatrix = hky(
                    kappa=kappa,
                    baseFrequencies=baseFrequencies
                )

                Alignment observed = data
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(7, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, DeltaExchangeOperator.class));

        assertTrue(containsOperator(operators, NodeHeightScaleOperator.class));
        assertTrue(containsOperator(operators, SubtreeSlideOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
    }

    private List<MCMCOperator> buildOperators(String source) throws Exception {
        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        BeastXState beastState =
                model.beastState;

        return new OperatorBuilder().build(beastState);
    }

    private boolean containsOperator(
            List<MCMCOperator> operators,
            Class<? extends MCMCOperator> operatorClass
    ) {
        return operators.stream()
                .anyMatch(operatorClass::isInstance);
    }

    @Test
    public void buildsTreeClockUpDownOperatorForStrictClockModel() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                Rate clockRate ~ LogNormal(logMean=0.0, logSd=1.0)
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=jc69(),
                    branchRates~StrictClock(clockRate=clockRate, tree=tree)
                ) observed as data
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, NodeHeightScaleOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
        assertTrue(containsOperator(operators, UpDownOperator.class));
    }

    @Test
    public void buildsTreeClockUpDownOperatorForStrictClockModelWithRenamedClockRate() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                Rate molecularRate ~ LogNormal(logMean=0.0, logSd=1.0)
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=jc69(),
                    branchRates~StrictClock(clockRate=molecularRate, tree=tree)
                ) observed as data
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, NodeHeightScaleOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
        assertTrue(containsOperator(operators, UpDownOperator.class));
    }
}