package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;

import java.util.Map;

public class NumSitesTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numSites";
    }

    GeneratorTileInput<DecoratedAlignment> alignmentInput = new GeneratorTileInput<>(
            "alignment"
    );

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(alignment.alignment().getSiteCount(), NonNegativeInt.INSTANCE);
    }

}
