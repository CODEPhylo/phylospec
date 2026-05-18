package tiles.substitutionmodels;

import dr.evolution.datatype.AminoAcids;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.aminoacid.EmpiricalAminoAcidModel;
import dr.evomodel.substmodel.aminoacid.WAG;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class WAGTile extends GeneratorTile<EmpiricalAminoAcidModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "wag";
    }

    GeneratorTileInput<Simplex, BeastXState> baseFrequenciesInput =
            new GeneratorTileInput<>("baseFrequencies", false);

    @Override
    public EmpiricalAminoAcidModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Simplex baseFrequencies =
                this.baseFrequenciesInput.apply(beastState, indexVariables);

        FrequencyModel frequencies;

        if (baseFrequencies == null) {
            frequencies = new FrequencyModel(
                    AminoAcids.INSTANCE,
                    WAG.INSTANCE.getEmpiricalFrequencies()
            );
        } else {
            if (baseFrequencies.size() != 20) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "WAG requires exactly 20 amino-acid base frequencies.",
                        "Provide a Simplex with 20 values.",
                        List.of("wag(baseFrequencies=repeat(0.05, num=20))")
                );
            }

            if (!baseFrequencies.isValid()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "WAG baseFrequencies must be a valid simplex.",
                        "All values must be in [0, 1] and sum to 1.",
                        List.of("wag(baseFrequencies=repeat(0.05, num=20))")
                );
            }

            frequencies = new FrequencyModel(
                    AminoAcids.INSTANCE,
                    baseFrequencies.getDoubleArray()
            );
        }

        return new EmpiricalAminoAcidModel(
                WAG.INSTANCE,
                frequencies
        );
    }
}