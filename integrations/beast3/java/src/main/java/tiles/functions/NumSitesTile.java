package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiling.BEASTState;

import java.util.Set;

public class NumSitesTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numSites";
    }

    GeneratorTileInput<DecoratedAlignment> alignmentInput = new GeneratorTileInput<>(
            "alignment",
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState) {
        DecoratedAlignment tree = this.alignmentInput.apply(beastState);
        return new IntScalarParam<>(tree.alignment().getSiteCount(), NonNegativeInt.INSTANCE);
    }

}
