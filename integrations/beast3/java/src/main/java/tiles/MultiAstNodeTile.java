package tiles;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import patternmatching.AstTemplateMatcher;
import patternmatching.BEASTState;
import patternmatching.Tile;
import patternmatching.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public abstract class MultiAstNodeTile<T> extends Tile<T> {

    protected abstract String getPhyloSpecTemplate();

    private final AstTemplateMatcher astTemplateMatcher;

    public MultiAstNodeTile() {
        this.astTemplateMatcher = new AstTemplateMatcher(this.getPhyloSpecTemplate());
    }

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, TypeResolver typeResolver, VariableResolver variableResolver) {
        Map<String, AstNode> matchedTemplateVariables = this.astTemplateMatcher.match(node, variableResolver);

        if (matchedTemplateVariables == null) {
            return Set.of();
        }

        // collect TileInput fields from this template in declaration order
        List<TileInput<?>> tileInputs = getInputs(this);

        if (tileInputs.size() != matchedTemplateVariables.size()) {
            return Set.of();
        }

        // build ordered list of compatible tiles per input
        List<String> orderedVars = new ArrayList<>();
        List<Set<Tile<?>>> compatibleInputTiles = new ArrayList<>();
        for (TileInput<?> tileInput : tileInputs) {
            AstNode inputAstNode = matchedTemplateVariables.get(tileInput.templateVariable);
            if (inputAstNode == null) return Set.of();

            Set<Tile<?>> compatible = tileInput.getCompatibleInputTiles(inputAstNode, allInputTiles);
            if (compatible.isEmpty()) return Set.of();

            orderedVars.add(tileInput.templateVariable);
            compatibleInputTiles.add(compatible);
        }

        // for each combination, create a freshly wired up tile
        Set<Tile<?>> wiredUpTiles = new HashSet<>();
        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    MultiAstNodeTile<T> wiredUpTile = (MultiAstNodeTile<T>) this.createInstance();

                    // get TileInput fields from fresh instance, keyed by template variable
                    Map<String, TileInput<?>> freshInputsByVar = new HashMap<>();
                    for (TileInput<?> freshInput : getInputs(wiredUpTile)) {
                        freshInputsByVar.put(freshInput.templateVariable, freshInput);
                    }

                    // wire each input tile and accumulate weight
                    int totalWeight = this.getPriority().getWeight();
                    for (int i = 0; i < orderedVars.size(); i++) {
                        Tile<?> inputTile = inputs.get(i);
                        freshInputsByVar.get(orderedVars.get(i)).setTile(inputTile);
                        totalWeight += inputTile.getWeight();
                    }

                    wiredUpTile.setWeight(totalWeight);
                    wiredUpTiles.add(wiredUpTile);
                }
        );

        return wiredUpTiles;
    }

    private static List<TileInput<?>> getInputs(MultiAstNodeTile<?> tile) {
        List<TileInput<?>> inputs = new ArrayList<>();
        for (Field field : tile.getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<?> input = (TileInput<?>) field.get(tile);
                    input.resolveTypeFromField(field);
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
        private TypeToken<O> expectedTypeToken;

        private Tile<O> tile;

        public TileInput(String templateVariable) {
            if (!templateVariable.startsWith("$")) {
                throw new RuntimeException("Invalid template variable '" + templateVariable + "'. A template variable has to start with a dollar sign (e.g. '$" + templateVariable + "'.");
            }
            this.templateVariable = templateVariable;
        }

        // called during reflection-based field scanning in tryToTile to resolve
        // the type token from the field's generic signature rather than requiring
        // it to be passed explicitly at the call site
        void resolveTypeFromField(Field field) {
            if (this.expectedTypeToken != null) return;
            // TileInput<O> — O is the first type argument
            ParameterizedType fieldType = (ParameterizedType) field.getGenericType();
            this.expectedTypeToken = (TypeToken<O>) TypeToken.of(fieldType.getActualTypeArguments()[0]);
        }

        public Set<Tile<?>> getCompatibleInputTiles(AstNode astNode, Map<AstNode, Set<Tile<?>>> inputTiles) {
            Set<Tile<?>> potentialInputs = inputTiles.get(astNode);
            if (potentialInputs == null) return Set.of();

            Set<Tile<?>> compatible = new HashSet<>();
            for (Tile<?> candidate : potentialInputs) {
                if (expectedTypeToken.isAssignableFrom(candidate.getTypeToken())) {
                    compatible.add(candidate);
                }
            }
            return compatible;
        }

        public void setTile(Tile<?> tile) {
            this.tile = (Tile<O>) tile;
        }

        public O apply(BEASTState beastState) {
            return this.tile.applyTile(beastState);
        }

        public TypeToken<?> getTypeToken() {
            return this.tile != null ? this.tile.getTypeToken() : expectedTypeToken;
        }
    }
}
