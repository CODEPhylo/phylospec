package tiles;

import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Stmt;
import org.phylospec.typeresolver.TypeResolver;
import patternmatching.*;

import java.util.Map;
import java.util.Set;

public class DrawTile extends AstNodeTile<Stmt.Draw> {

    @Override
    public Class<Stmt.Draw> getTargetNodeType() {
        return Stmt.Draw.class;
    }

    @Override
    public Set<EvaluatedTile> tryToTileExpr(Stmt.Draw expr, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver) {
        Set<EvaluatedTile> possibleInputs = inputTiles.get(expr.expression);
        EvaluatedTile matchingInput = DrawTile.getBestInput(possibleInputs, new TypeToken<EvaluatedDistribution<? extends Distribution>>() {
        });

        if (matchingInput == null) {
            // there is no distribution input
            // we cannot apply this tile
            return Set.of();
        }

        EvaluatedDistribution<?> evaluatedDistribution = (EvaluatedDistribution<?>) matchingInput.generatedObject();

        // set the variable name as the ID of the state node

        evaluatedDistribution.stateNode().setID(expr.name);

        // we now return the state node as the generated object

        return Set.of(
                new EvaluatedTile(
                        this,
                        evaluatedDistribution.stateNode(),
                        evaluatedDistribution.stateNodeType(),
                        matchingInput.score() + 1,
                        Set.of(evaluatedDistribution.stateNode()),
                        Set.of(evaluatedDistribution.distribution()),
                        evaluatedDistribution.operatorSet()
                )
        );
    }
}
