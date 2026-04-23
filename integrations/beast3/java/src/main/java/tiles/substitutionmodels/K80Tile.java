package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class K80Tile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "k80";
    }

    GeneratorTileInput<RealScalar<PositiveReal>> kappaInput = new GeneratorTileInput<>("kappa");

    @Override
    public HKY applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> kappa = this.kappaInput.apply(beastState, indexVariables);

        // k80 = hky with equal base frequencies
        SimplexParam equalFreqs = new SimplexParam(new double[]{0.25, 0.25, 0.25, 0.25});

        // initialize frequencies

        Frequencies frequencies = new Frequencies();
        beastState.setInput(frequencies, frequencies.frequenciesInput, equalFreqs);

        // initialize HKY

        HKY hky = new HKY();
        beastState.setInput(hky, hky.kappaInput, kappa);
        beastState.setInput(hky, hky.frequenciesInput, frequencies);

        return hky;
    }

}
