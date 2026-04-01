package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class MultiAstNodeTile extends Tile {

    protected abstract String getPhyloSpecTemplate();

    AstTemplateMatcher astTemplateMatcher;

    public MultiAstNodeTile() {
        this.astTemplateMatcher = new AstTemplateMatcher(this.getPhyloSpecTemplate());
    }

    @Override
    public Set<EvaluatedTile> tryToTile(AstNode node, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver, VariableResolver variableResolver) {
        // match the PhyloSpec template

        Map<String, AstNode> matchedTemplateVariables = this.astTemplateMatcher.match(node, variableResolver);

        if (matchedTemplateVariables == null) {
            // we could not match the template
            // the tile cannot be applied
            return Set.of();
        }

        // compare the inputs specified with TileInput fields with the template inputs

        Set<TileInput<?>> tileInputs = this.getInputs();

        if (tileInputs.size() != matchedTemplateVariables.size()) {
            // the number of inputs does not match
            // the tile cannot be applied
            return Set.of();
        }

        int inputWeight = 0;

        for (TileInput<?> tileInput : tileInputs) {
            AstNode inputAstNode = matchedTemplateVariables.get(tileInput.templateVariable);

            if (inputAstNode == null) {
                // this PhyloSpec template input has not been specified in a TileInput field
                return Set.of();
            }

            if (!tileInput.canBeApplied(inputAstNode, inputTiles)) {
                // the types don't match
                return Set.of();
            }

            // the input matches: we apply it

            inputWeight += tileInput.apply(inputAstNode, inputTiles);
        }

        // now that the inputs have been applied, we can apply the tile

        Set<EvaluatedTile> evaluatedTiles = this.applyTile();

        // update weights

        int totalWeight = inputWeight + this.getPriority().getWeight();
        return evaluatedTiles.stream().map(t -> t.withWeight(totalWeight)).collect(Collectors.toSet());
    }

    abstract public Set<EvaluatedTile> applyTile();

    private Set<TileInput<?>> getInputs() {
        // the expected inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST inputs)
        // we use reflection to get the expected inputs
        Set<TileInput<?>> inputs = new HashSet<>();

        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<?> input = (TileInput<?>) field.get(this);
                    inputs.add(input);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return inputs;
    }


    public static class TileInput<O> {
        private final String templateVariable;
        private final TypeToken<O> expectedTypeToken;

        private O value;
        private Type valueType;

        public TileInput(String templateVariable, TypeToken<O> typeToken) {
            if (!templateVariable.startsWith("$")) {
                throw new RuntimeException("Invalid template variable '" + templateVariable + "'. A template variable has to start with a dollar sign (e.g. '$" + templateVariable + "'.");
            }

            this.templateVariable = templateVariable;
            this.expectedTypeToken = typeToken;
        }

        public boolean canBeApplied(AstNode astNode, Map<AstNode, Set<EvaluatedTile>> inputTiles) {
            Set<EvaluatedTile> potentialInputs = inputTiles.get(astNode);
            EvaluatedTile bestInput = TileUtils.getBestInput(potentialInputs, this.expectedTypeToken);
            return bestInput != null;
        }

        public int apply(AstNode astNode, Map<AstNode, Set<EvaluatedTile>> inputTiles) {
            Set<EvaluatedTile> potentialInputs = inputTiles.get(astNode);
            EvaluatedTile bestInputTile = TileUtils.getBestInput(potentialInputs, this.expectedTypeToken);
            this.value = (O) bestInputTile.generatedObject();
            this.valueType = bestInputTile.generatedType();
            return bestInputTile.weight();
        }

        public O get() {
            return this.value;
        }

        public Type getType() {
            return this.valueType;
        }

    }

}
