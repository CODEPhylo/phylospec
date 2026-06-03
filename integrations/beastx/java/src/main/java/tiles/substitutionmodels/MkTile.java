package tiles.substitutionmodels;

import dr.evolution.datatype.TwoStates;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.GeneralSubstitutionModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class MkTile extends GeneratorTile<GeneralSubstitutionModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "mk";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> rateInput =
            new GeneratorTileInput<>("rate", false);

    @Override
    public GeneralSubstitutionModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> rate =
                this.rateInput.apply(beastState, indexVariables);

        Parameter rateParameter =
                rate == null
                        ? new Parameter.Default(1.0)
                        : toParameter(rate);

        FrequencyModel frequencies =
                new FrequencyModel(
                        TwoStates.INSTANCE,
                        new double[]{0.5, 0.5}
                );

        GeneralSubstitutionModel model =
                new GeneralSubstitutionModel(
                        "mk",
                        TwoStates.INSTANCE,
                        frequencies,
                        rateParameter,
                        -1
                );

        /*
         * For the binary Mk case there is only one exchangeability parameter.
         * Keeping normalization off makes the PhyloSpec `rate` argument control
         * the transition intensity instead of being normalized away.
         */
        model.setNormalization(false);

        if (rateParameter.getDimension() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "mk currently supports a single expected rate.",
                    "Use mk(rate=1.0) or mk(rate~LogNormal(...)).",
                    List.of("QMatrix traitQ = mk(rate=1.0)")
            );
        }

        return model;
    }

    private static Parameter toParameter(RealScalar<? extends PositiveReal> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}