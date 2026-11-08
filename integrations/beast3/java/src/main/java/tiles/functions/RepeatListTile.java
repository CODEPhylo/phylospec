package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.Stochasticity;

/** Repeat one mapped value by reference; repetition never creates independent draws. */
public class RepeatListTile extends GeneratorTile<List<Object>, BEASTState> {
    GeneratorTileInput<Object, BEASTState> valueInput = new GeneratorTileInput<>("value");
    GeneratorTileInput<IntScalar<? extends Int>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.math");
    }

    @Override
    public List<Object> applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        int count = fixedCount(numInput.apply(state, indices), getRootNode());
        // Map once: every position must retain the very same scalar, object, or nested list.
        return Collections.nCopies(count, valueInput.apply(state, indices));
    }

    private static int fixedCount(IntScalar<? extends Int> number, AstNode source) {
        int count = number.get();
        if (count <= 0) {
            throw new TileApplicationError(
                    source, "repeat requires a positive length.", "Use a fixed integer num greater than zero.");
        }
        return count;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> element = valueInput.getTypeToken();
        return element == null ? super.getTypeToken() : TypeToken.listOf(element);
    }

    @Override
    public TilePriority getPriority() {
        // Keep the existing fixed numeric vector and literal Simplex representations preferred.
        return TilePriority.LOW;
    }
}
