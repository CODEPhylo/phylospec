package tiles;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.type.RealScalar;
import patternmatching.*;

import java.util.Map;
import java.util.Set;

public class OffsetTile extends MultiAstNodeTile {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               Real x ~ $distribution
               x + $offset
               """;
    }

    TileInput<EvaluatedDistribution<? extends ScalarDistribution<RealScalar<Real>, Double>>> distributionInput = new TileInput<>(
            "$distribution", new TypeToken<>() {}
    );
    TileInput<Double> offsetInput = new TileInput<>("$offset", new TypeToken<>() {});

    @Override
    public Set<EvaluatedTile> applyTile(BEASTState beastState) {
        EvaluatedDistribution<? extends ScalarDistribution<RealScalar<Real>, Double>> distribution = this.distributionInput.get();
        Double offset = this.offsetInput.get();

        OffsetReal offsetDistribution = new OffsetReal(distribution.distribution(), offset);

        return Set.of(
               new EvaluatedTile(
                       this,
                       // we keep the same state node
                       distribution.stateNode(),
                       distribution.stateNodeType(),
                       Set.of(),
                       // we replace the prior on the state node with the offset distribution
                       Map.of(distribution.stateNode(), offsetDistribution),
                       Set.of()
               )
        );
    }

}
