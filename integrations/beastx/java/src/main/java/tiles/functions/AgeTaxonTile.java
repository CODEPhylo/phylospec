package tiles.functions;

import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class AgeTaxonTile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "age";
    }

    GeneratorTileInput<Taxon, BeastXState> taxonInput =
            new GeneratorTileInput<>("taxon");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Taxon taxon =
                this.taxonInput.apply(beastState, indexVariables);

        double age =
                taxon.getHeight();

        if (age < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Taxon age must be non-negative.",
                    "Use a taxon with age >= 0.",
                    java.util.List.of("Taxon t = taxon(name=\"taxon1\", age=1.0)")
            );
        }

        return new BeastXRealScalarParam<>(
                age,
                NonNegativeReal.INSTANCE
        );
    }
}