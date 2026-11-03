package tiles.input;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;

public class ParserTile {

    public static class Delimiter extends GeneratorTile<DelimiterParser, BEASTState> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        GeneratorTileInput<String, BEASTState> delimiterInput =
                new GeneratorTileInput<>("delimiter", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
        // Retain boxed integers as well as native integer scalars: a named value may
        // also feed an existing input (such as subset.start) that requires Integer.
        GeneratorTileInput<Object, BEASTState> partInput =
                new GeneratorTileInput<>("part", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)) {
                    @Override
                    public Set<Tile<?, BEASTState>> getCompatibleInputTiles(
                            AstNode node,
                            Map<AstNode, Set<Tile<?, BEASTState>>> candidates,
                            StochasticityResolver stochasticityResolver)
                            throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
                        Set<Tile<?, BEASTState>> compatible =
                                super.getCompatibleInputTiles(node, candidates, stochasticityResolver);
                        TypeToken<?> boxedInteger = TypeToken.of(Integer.class);
                        TypeToken<?> integerScalar = new TypeToken<IntScalar<? extends Int>>() {};
                        compatible.removeIf(tile -> !boxedInteger.isAssignableFrom(tile.getTypeToken())
                                && !integerScalar.isAssignableFrom(tile.getTypeToken()));
                        if (node instanceof Expr.AssignedArgument argument
                                && argument.expression instanceof Expr.Literal literal
                                && literal.value instanceof Integer value
                                && value > 0) {
                            // Keep the original boxed representation for a direct literal; its
                            // equivalent wrappers otherwise multiply shared alignment candidates.
                            // Named/computed inputs retain all compatible representations.
                            compatible.removeIf(tile -> !boxedInteger.isAssignableFrom(tile.getTypeToken()));
                        }
                        return compatible;
                    }
                };

        @Override
        public DelimiterParser applyTile(
                BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            Object part = this.partInput.apply(beastState, indexVariables);
            int position = part instanceof Integer value ? value : ((IntScalar<?>) part).get();
            return new DelimiterParser(this.delimiterInput.apply(beastState, indexVariables), position);
        }
    }

    public static class Regex extends GeneratorTile<RegexParser, BEASTState> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        GeneratorTileInput<String, BEASTState> regexInput = new GeneratorTileInput<>("regex");

        @Override
        public RegexParser applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return new RegexParser(this.regexInput.apply(beastState, indexVariables));
        }
    }

    public sealed interface Parser {
        String parse(String raw);
    }

    public static final class DelimiterParser implements Parser {

        private final String delimiter;
        private final Integer part;

        public DelimiterParser(String delimiter, Integer part) {
            if (part < 1) {
                throw new TileApplicationError(
                        "Parser part must be at least 1.", "Use a one-based field position; received " + part + ".");
            }
            this.delimiter = delimiter;
            this.part = part;
        }

        @Override
        public String parse(String raw) {
            return raw.split(Pattern.quote(this.delimiter))[this.part - 1];
        }
    }

    public static final class RegexParser implements Parser {

        private final String regex;

        public RegexParser(String regex) {
            this.regex = regex;
        }

        @Override
        public String parse(String raw) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(raw);

            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new TileApplicationError(
                        "Regex cannot be matched for input '" + raw + " '.", "Is the regex correct?");
            }
        }
    }
}
