package tiles.input;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserTile {

    public static class Delimiter extends GeneratorTile<DelimiterParser, BeastXState> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        GeneratorTileInput<String, BeastXState> delimiterInput =
                new GeneratorTileInput<>(
                        "delimiter",
                        Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
                );

        GeneratorTileInput<Integer, BeastXState> partInput =
                new GeneratorTileInput<>(
                        "part",
                        Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
                );

        @Override
        public DelimiterParser applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return new DelimiterParser(
                    this.delimiterInput.apply(beastState, indexVariables),
                    this.partInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class Regex extends GeneratorTile<RegexParser, BeastXState> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        GeneratorTileInput<String, BeastXState> regexInput =
                new GeneratorTileInput<>(
                        "regex",
                        Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
                );

        @Override
        public RegexParser applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return new RegexParser(
                    this.regexInput.apply(beastState, indexVariables)
            );
        }
    }

    public sealed interface Parser permits DelimiterParser, RegexParser {
        String parse(String raw);
    }

    public static final class DelimiterParser implements Parser {

        private final String delimiter;
        private final Integer part;

        public DelimiterParser(
                String delimiter,
                Integer part
        ) {
            this.delimiter = delimiter;
            this.part = part;
        }

        @Override
        public String parse(String raw) {
            String[] parts =
                    raw.split(Pattern.quote(this.delimiter));

            int index =
                    this.part - 1;

            if (index < 0 || index >= parts.length) {
                throw new TileApplicationError(
                        "Delimiter parser could not extract part " + this.part + " from input '" + raw + "'.",
                        "Check the delimiter and the one-based part index."
                );
            }

            return parts[index];
        }
    }

    public static final class RegexParser implements Parser {

        private final String regex;

        public RegexParser(String regex) {
            this.regex = regex;
        }

        @Override
        public String parse(String raw) {
            Pattern pattern =
                    Pattern.compile(this.regex);

            Matcher matcher =
                    pattern.matcher(raw);

            if (matcher.find()) {
                return matcher.group(1);
            }

            throw new TileApplicationError(
                    "Regex cannot be matched for input '" + raw + "'.",
                    "Check that the regex has one capturing group and matches the input."
            );
        }
    }
}