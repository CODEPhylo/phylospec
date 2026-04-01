package tiles;

import beast.base.inference.Distribution;
import org.phylospec.ast.Stmt;
import patternmatching.*;

import java.util.Map;
import java.util.Set;

public class DrawTile extends AstNodeTile<Stmt.Draw> {

    TileInput<Stmt.Draw, EvaluatedDistribution<? extends Distribution>> evaluatedDistributionInput = new TileInput<>(
            expr -> expr.expression,
            new TypeToken<>() {}
    );

    @Override
    public Class<Stmt.Draw> getTargetNodeType() {
        return Stmt.Draw.class;
    }

    @Override
    public Set<EvaluatedTile> applyTile(Stmt.Draw expr) {
        EvaluatedDistribution<? extends Distribution> evaluatedDistribution = this.evaluatedDistributionInput.get();

        // set the variable name as the ID of the state node

        evaluatedDistribution.stateNode().setID(expr.name);

        // we now return the state node as the generated object

        return Set.of(
                new EvaluatedTile(
                        this,
                        evaluatedDistribution.stateNode(),
                        evaluatedDistribution.stateNodeType(),
                        Set.of(evaluatedDistribution.stateNode()),
                        Map.of(evaluatedDistribution.stateNode(), evaluatedDistribution.distribution()),
                        evaluatedDistribution.operatorSet()
                )
        );
    }
}
