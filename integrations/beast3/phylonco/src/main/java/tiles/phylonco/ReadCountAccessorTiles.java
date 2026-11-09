package tiles.phylonco;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

/** {@code taxa}, {@code numTaxa} and {@code numSites} for read-count data. */
public final class ReadCountAccessorTiles {

    private ReadCountAccessorTiles() {}

    public static class Taxa extends GeneratorTile<List<TaxonValue>, BEASTState> {
        GeneratorTileInput<ReadCountData, BEASTState> readCountsInput = new GeneratorTileInput<>("readCounts");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "taxa";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.functions.accessor");
        }

        @Override
        public TaxaData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return this.readCountsInput.apply(state, indices).taxa();
        }
    }

    public static class NumTaxa extends GeneratorTile<IntScalarParam<NonNegativeInt>, BEASTState> {
        GeneratorTileInput<ReadCountData, BEASTState> readCountsInput = new GeneratorTileInput<>("readCounts");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "numTaxa";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.functions.accessor");
        }

        @Override
        public IntScalarParam<NonNegativeInt> applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            int cells = this.readCountsInput.apply(state, indices).numCells();
            return new IntScalarParam<>(cells, NonNegativeInt.INSTANCE);
        }
    }

    public static class NumSites extends GeneratorTile<IntScalarParam<NonNegativeInt>, BEASTState> {
        GeneratorTileInput<ReadCountData, BEASTState> readCountsInput = new GeneratorTileInput<>("readCounts");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "numSites";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.functions.accessor");
        }

        @Override
        public IntScalarParam<NonNegativeInt> applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return new IntScalarParam<>(this.readCountsInput.apply(state, indices).numSites(), NonNegativeInt.INSTANCE);
        }
    }
}
