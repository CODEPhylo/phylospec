package patternmatching;

import java.util.*;

public abstract class FunctionTile<T> extends GeneratorTile<T> {

    @Override
    protected Set<EvaluatedTile> operateTile(int score) {
        T generatedObject = this.operateTile();
        return Set.of(
                new EvaluatedTile(this, generatedObject, this.generatedType, score)
        );
    }

    protected abstract T operateTile();

}
