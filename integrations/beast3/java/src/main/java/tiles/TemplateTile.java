package tiles;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import templatematching.AstTemplateMatcher;
import tiling.FailedTilingAttempt;
import tiling.Tile;
import tiling.TileFactory;
import tiling.TileInput;

import java.lang.reflect.Field;
import java.util.*;

/**
 * This class represents tiles that cover multiple AstNodes. Extend this class for custom tiles and specify a
 * PhyloSpec template used to match the AST subgraph.
 * Use TemplateTileInput fields to specify the tile inputs (similar to BEAST 2.8 inputs).
 */
public abstract class TemplateTile<T> extends Tile<T> implements TileFactory {

    protected abstract String getPhyloSpecTemplate();

    protected List<String> getPhyloSpecTemplates() {
        return List.of(this.getPhyloSpecTemplate());
    };

    private List<AstTemplateMatcher> astTemplateMatchers;


    @Override
    public Set<Tile<?>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (this.astTemplateMatchers == null) {
            this.astTemplateMatchers = new ArrayList<>();

            for (String template : this.getPhyloSpecTemplates()) {
                this.astTemplateMatchers.add(
                        new AstTemplateMatcher(template)
                );
            }
        }

        // try to match any of the templates

        Map<String, AstNode> matchedTemplateVariables = null;
        for (AstTemplateMatcher templateMatcher : this.astTemplateMatchers) {
            matchedTemplateVariables = templateMatcher.match(node, variableResolver);

            if (matchedTemplateVariables != null) break;
        }

        if (matchedTemplateVariables == null) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        // check the stochasticity

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage("BEAST 2.8", stochasticity, this.getCompatibleStochasticities())
            );
        }

        // collect TileInput fields from this template in declaration order

        List<TileInput<?>> tileInputs = this.getTileInputs();

        // build ordered list of compatible tiles per input

        List<TileInput<?>> usedInputs = new ArrayList<>();
        List<Set<Tile<?>>> compatibleInputTiles = new ArrayList<>();
        for (TileInput<?> tileInput : tileInputs) {
            AstNode inputAstNode = matchedTemplateVariables.get(tileInput.getKey());

            if (inputAstNode == null) {
                if (!tileInput.isRequired()) {
                    continue;
                } else {
                    throw new FailedTilingAttempt.Rejected(
                            "BEAST 2.8 expects you to provide a value for '" + tileInput.getKey() + "'."
                    );
                }
            }

            Set<Tile<?>> compatible = tileInput.getCompatibleInputTiles(inputAstNode, allInputTiles, stochasticityResolver);
            if (compatible.isEmpty()) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        "BEAST 2.8 cannot deal with the value you provided for " + tileInput.getKey().replace("$", "") + "."
                );
            }

            compatibleInputTiles.add(compatible);
            usedInputs.add(tileInput);
        }

        // for each combination, create a freshly wired up tile
        return this.getWiredUpTiles(usedInputs, compatibleInputTiles, node);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getClass().getSimpleName());
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType().equals(TemplateTileInput.class)) {
                field.setAccessible(true);
                try {
                    TemplateTileInput<?> input = (TemplateTileInput<?>) field.get(this);
                    Tile<?> child = input.getTile();
                    if (child != null) {
                        // strip leading $ from template variable name
                        String varName = input.getKey().substring(1);
                        sb.append(" (").append(varName).append(" ").append(child).append(")");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public static class TemplateTileInput<O> extends TileInput<O> {
        private final String templateVariable;

        public TemplateTileInput(String templateVariable) {
            this(templateVariable, true, EnumSet.allOf(Stochasticity.class));
        }

        public TemplateTileInput(String templateVariable, boolean required) {
            this(templateVariable, required, EnumSet.allOf(Stochasticity.class));
        }

        public TemplateTileInput(String templateVariable, Set<Stochasticity> acceptedStochasticities) {
            this(templateVariable, true, acceptedStochasticities);
        }

        public TemplateTileInput(String templateVariable, boolean required, Set<Stochasticity> acceptedStochasticities) {
            super(required, acceptedStochasticities);
            if (!templateVariable.startsWith("$")) {
                throw new RuntimeException("Invalid template variable '" + templateVariable + "'. A template variable has to start with a dollar sign (e.g. '$" + templateVariable + "'.");
            }
            if (!this.isRequired() && !templateVariable.startsWith("$$")) {
                throw new RuntimeException("Invalid template variable '" + templateVariable + "'. An optional template variable has to start with two dollar signs.");
            }
            this.templateVariable = templateVariable;
        }


        @Override
        public String getKey() {
            return this.templateVariable;
        }
    }
}
