package mcmc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.Operator;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.SimplexParam;
import beastconfig.BEASTState;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;

/// A simplex whose elements set partition rates gets BEAUti's partition-rate operator tuning;
/// any other simplex keeps the default.
public class PartitionRateOperatorTest {

    private static final String MODEL = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
            Simplex partitionWeights ~ Dirichlet(concentration=[1.0, 1.0, 1.0])
            Simplex freqs ~ Dirichlet(concentration=[1.0, 1.0, 1.0, 1.0])
            Alignment a ~ PhyloCTMC(tree, qMatrix=hky(kappa=2.0, baseFrequencies=freqs),
                                    rate=3.0 * partitionWeights[1]) observed as data
            Alignment b ~ PhyloCTMC(tree, qMatrix=jc69(), rate=3.0 * partitionWeights[2]) observed as data
            Alignment c ~ PhyloCTMC(tree, qMatrix=jc69(), rate=3.0 * partitionWeights[3]) observed as data
            """;

    @Test
    public void testPartitionWeightsGetPartitionRateTuning() {
        BEASTState state = tile(MODEL);

        DeltaExchangeOperator weights = deltaExchangeOn(state, 3);
        assertEquals(0.25, weights.deltaInput.get(), 1e-12);
        assertEquals(2.0, weights.m_pWeight.get(), 1e-12);
    }

    @Test
    public void testOtherSimplexKeepsDefaultTuning() {
        BEASTState state = tile(MODEL);

        DeltaExchangeOperator frequencies = deltaExchangeOn(state, 4);
        assertEquals(0.01, frequencies.deltaInput.get(), 1e-12);
        assertEquals(5.0, frequencies.m_pWeight.get(), 1e-12);
    }

    /// The delta-exchange operator on the only simplex of the given dimension.
    private static DeltaExchangeOperator deltaExchangeOn(BEASTState state, int dimension) {
        List<DeltaExchangeOperator> found = new ArrayList<>();
        for (Operator operator : state.operators) {
            if (operator instanceof DeltaExchangeOperator d
                    && d.rvparameterInput.get() instanceof SimplexParam simplex
                    && simplex.size() == dimension) {
                found.add(d);
            }
        }
        assertEquals(1, found.size(), "expected one delta-exchange operator on a simplex of size " + dimension);
        return found.getFirst();
    }

    private static BEASTState tile(String source) {
        List<Token> tokens = new Lexer(source).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BEASTState> evaluateTiles =
                new EvaluateTiles<>(new BeastCoreTileLibrary().getTiles(), variableResolver, stochasticityResolver);
        BEASTState state = new BEASTState("test");

        PrintStream original = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            List<Tile<?, BEASTState>> tiling = evaluateTiles.getBestTiling(statements);
            assertTrue(tiling.stream().noneMatch(Objects::isNull), "The source could not be tiled");
            for (Tile<?, BEASTState> tile : tiling) {
                tile.apply(state, new IdentityHashMap<>());
            }
        } finally {
            System.setOut(original);
        }
        return state;
    }
}
