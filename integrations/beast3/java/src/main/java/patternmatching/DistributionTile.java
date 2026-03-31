package patternmatching;

import beast.base.inference.Distribution;

import java.util.Set;

public abstract class DistributionTile<T extends Distribution> extends GeneratorTile<T> {

    @Override
    protected Set<EvaluatedTile> operateTile(int score) {
        EvaluatedDistribution<T> generatedObject = this.operateTile();
        return Set.of(
                new EvaluatedTile(
                        this,
                        generatedObject,
                        TypeToken.parameterized(EvaluatedDistribution.class, generatedType).getType(),
                        score
                )
        );
    }

    protected abstract EvaluatedDistribution<T> operateTile();

}
