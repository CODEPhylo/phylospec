package tiles.phylonco;

import beastconfig.BEASTState;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

/** {@code fromMpileup(file, cellNames)}: read counts of every cell and site in an mpileup file. */
public class FromMpileupTile extends GeneratorTile<ReadCountData, BEASTState> {

    GeneratorTileInput<String, BEASTState> fileInput =
            new GeneratorTileInput<>("file", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<List<?>, BEASTState> cellNamesInput =
            new GeneratorTileInput<>("cellNames", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromMpileup";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylonco");
    }

    @Override
    public ReadCountData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        String path = this.fileInput.apply(state, indices);
        List<String> cellNames = this.cellNamesInput.apply(state, indices).stream().map(String::valueOf).toList();

        MpileupReader.Counts counts;
        try {
            counts = MpileupReader.read(state.resolvePath(path), cellNames);
        } catch (IOException e) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Could not read the mpileup file. " + e.getMessage(),
                    "Check the file path and that cellNames has one entry per cell column.");
        }
        if (counts.numSites() == 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "The mpileup file has no usable sites for " + cellNames.size() + " cells.",
                    "Every line needs chrom, pos, ref and three columns (depth, bases, qualities) per cell.");
        }
        return ReadCountData.of(counts, state);
    }
}
