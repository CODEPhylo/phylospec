package patternmatching;

import java.util.*;

public abstract class FunctionTile<T> extends GeneratorTile<T> {

    @Override
    protected Set<EvaluatedTile> applyTile() {
        T generatedObject = this.apply();
        return Set.of(
                new EvaluatedTile(this, generatedObject, this.generatedType)
        );
    }

    protected abstract T apply();

}
