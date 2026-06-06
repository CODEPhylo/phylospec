package tiles.distributions;

import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.datatype.Codons;
import dr.evolution.sequence.Sequence;
import dr.evomodel.branchmodel.HomogeneousBranchModel;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.branchratemodel.StrictClockBranchRates;
import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.substmodel.codon.GY94CodonModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.model.UnboundDistribution;

import java.util.IdentityHashMap;

public class PhyloCTMCTile extends GeneratorTile<
        UnboundDistribution<Alignment>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "PhyloCTMC";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    GeneratorTileInput<SubstitutionModel, BeastXState> substitutionModelInput =
            new GeneratorTileInput<>("qMatrix");

    GeneratorTileInput<BranchRateModel, BeastXState> branchRateModelInput =
            new GeneratorTileInput<>("branchRates", false);

    GeneratorTileInput<Partial<GammaSiteRateModel, SubstitutionModel>, BeastXState> siteRateModelInput =
            new GeneratorTileInput<>("siteRates", false);

    @Override
    public UnboundDistribution<Alignment> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        SubstitutionModel substitutionModel =
                this.substitutionModelInput.apply(beastState, indexVariables);

        BranchRateModel branchRateModel =
                this.branchRateModelInput.apply(beastState, indexVariables);

        Partial<GammaSiteRateModel, SubstitutionModel> partialSiteRateModel =
                this.siteRateModelInput.apply(beastState, indexVariables);

        GammaSiteRateModel siteRateModel;
        if (partialSiteRateModel != null) {
            siteRateModel = partialSiteRateModel.complete(substitutionModel);
        } else {
            siteRateModel = new GammaSiteRateModel("siteRateModel");
            siteRateModel.setSubstitutionModel(substitutionModel);
        }

        HomogeneousBranchModel branchModel =
                new HomogeneousBranchModel(substitutionModel);

        if (branchRateModel == null) {
            branchRateModel =
                    new StrictClockBranchRates(new Parameter.Default(1.0));
        }

        BranchRateModel finalBranchRateModel =
                branchRateModel;

        return new UnboundDistribution<>((observedAlignment, id) -> {
            Alignment likelihoodAlignment =
                    observedAlignmentForSubstitutionModel(
                            observedAlignment,
                            substitutionModel
                    );

            BeastXPhyloCTMCLikelihoodSpec likelihoodSpec =
                    new BeastXPhyloCTMCLikelihoodSpec(
                            id,
                            likelihoodAlignment,
                            tree,
                            branchModel,
                            siteRateModel,
                            finalBranchRateModel
                    );

            beastState.addLikelihoodDistribution(likelihoodSpec, id);
        });
    }

    private Alignment observedAlignmentForSubstitutionModel(
            Alignment observedAlignment,
            SubstitutionModel substitutionModel
    ) {
        if (substitutionModel instanceof GY94CodonModel) {
            return codonAlignment(observedAlignment);
        }

        return observedAlignment;
    }

    private Alignment codonAlignment(Alignment observedAlignment) {
        if (observedAlignment.getSiteCount() % 3 != 0) {
            throw new TileApplicationError(
                    "GY94 PhyloCTMC requires an observed alignment whose number of sites is divisible by 3.",
                    "Use subset(..., start=..., end=...) so the observed alignment contains complete codons."
            );
        }

        SimpleAlignment codonAlignment =
                new SimpleAlignment();

        codonAlignment.setDataType(Codons.UNIVERSAL);

        for (int sequenceIndex = 0; sequenceIndex < observedAlignment.getSequenceCount(); sequenceIndex++) {
            String sequenceString =
                    observedAlignment.getAlignedSequenceString(sequenceIndex);

            if (sequenceString.length() % 3 != 0) {
                throw new TileApplicationError(
                        "GY94 PhyloCTMC requires every sequence length to be divisible by 3.",
                        "Check the observed alignment or subset it to complete codons."
                );
            }

            Sequence sequence =
                    new Sequence(
                            observedAlignment.getTaxon(sequenceIndex),
                            sequenceString
                    );

            sequence.setDataType(Codons.UNIVERSAL);

            codonAlignment.addSequence(sequence);
        }

        codonAlignment.updateSiteCount();

        return codonAlignment;
    }
}