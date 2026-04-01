package patternmatching;

import beast.base.inference.Distribution;

import java.util.Set;

public abstract class DistributionTile<T extends Distribution> extends GeneratorTile<T> {

    @Override
    protected Set<EvaluatedTile> applyTile() {
        EvaluatedDistribution<T> generatedObject = this.apply();
        return Set.of(
                new EvaluatedTile(
                        this,
                        generatedObject,
                        TypeToken.parameterized(EvaluatedDistribution.class, this.generatedType).getType()
                )
        );
    }

    protected abstract EvaluatedDistribution<T> apply();

}
