import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.Tile;
import tiles.misc.LiteralTile;
import tiling.BeastXState;
import tiling.WiredInputs;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tiles that keep their children in a list (loggers, vector and taxon lists) report them to the
 * tiling consistency check, so a draw used in several places is built once.
 */
class WiredInputsTest {

    /** Two different tilings of the same expression in one candidate are inconsistent. */
    @Test
    void differentTilesForTheSameNodeAreInconsistent() {
        Expr.Literal literal = new Expr.Literal(1.0);
        Tile<?, BeastXState> first = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, literal);
        Tile<?, BeastXState> second = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, literal);

        assertThrows(InconsistentTilingException.class, () -> WiredInputs.of(List.of(first, second)));
    }

    /** The same tile used twice is one input. */
    @Test
    void sameTileTwiceIsOneInput() throws Throwable {
        Expr.Literal literal = new Expr.Literal(1.0);
        Tile<?, BeastXState> tile = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, literal);

        assertEquals(1, WiredInputs.of(List.of(tile, tile)).size());
    }

    /**
     * a is used by another draw and by the logger. Before, the logger could use another, equivalent
     * tiling of a, and a second sampled parameter a_2 was created.
     */
    @Test
    void variableLoggedAndUsedElsewhereIsCreatedOnce() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                PositiveReal a ~ LogNormal(logMean=1.0, logSd=0.3)
                Real x ~ Normal(mean=a, sd=1.0)

                mcmc {
                    Integer chainLength = 10
                    Logger fileLogger = fileLogger(logEvery=1, file="target/wiredInputs.log", parameters=[a, x])
                }
                """).buildState("wiredInputs");

        Set<String> ids = state.stateNodes.keySet().stream().map(Parameter::getId).collect(Collectors.toSet());
        assertEquals(Set.of("a", "x"), ids);
    }
}
