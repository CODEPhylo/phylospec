package tiles.phylonco;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import phylonco.beast.evolution.substitutionmodel.GT10;

/** {@code gt10(relativeRates, genotypeFrequencies)} -> phylonco GT10 (10 unphased genotypes). */
public class GT10Tile extends GeneratorTile<GT10, BEASTState> {

    GeneratorTileInput<Simplex, BEASTState> relativeRatesInput = new GeneratorTileInput<>("relativeRates");
    GeneratorTileInput<Simplex, BEASTState> genotypeFrequenciesInput = new GeneratorTileInput<>("genotypeFrequencies");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "gt10";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylonco");
    }

    @Override
    public GT10 applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        Simplex relativeRates = this.relativeRatesInput.apply(state, indices);
        Simplex genotypeFrequencies = this.genotypeFrequenciesInput.apply(state, indices);
        if (relativeRates.size() != 6 || genotypeFrequencies.size() != 10) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "gt10 needs 6 relative rates and 10 genotype frequencies, but got " + relativeRates.size()
                            + " and " + genotypeFrequencies.size() + ".",
                    "Use relativeRates in AC, AG, AT, CG, CT, GT order and one frequency per unphased genotype.");
        }

        // phylonco's GT10 reads the six rates by key from a RealVectorParam
        if (!(relativeRates instanceof RealVectorParam<?> ratesParameter)) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "gt10 needs relativeRates to be a parameter (e.g. drawn from a Dirichlet or a literal vector).",
                    "Draw relativeRates from a Dirichlet distribution.");
        }
        if (ratesParameter.keysInput.get() == null) {
            // the parameter is usually already initialised (e.g. by the Dirichlet tile), and its
            // key map is built in initAndValidate, so set the keys directly and rebuild the map
            ratesParameter.keysInput.setValue("AC AG AT CG CT GT", ratesParameter);
            ratesParameter.initAndValidate();
        }

        Frequencies frequencies = new Frequencies();
        state.setInput(frequencies, frequencies.frequenciesInput, genotypeFrequencies);

        GT10 gt10 = new GT10();
        state.setInput(gt10, gt10.nucRatesInput, relativeRates);
        state.setInput(gt10, gt10.frequenciesInput, frequencies);
        return gt10;
    }
}
