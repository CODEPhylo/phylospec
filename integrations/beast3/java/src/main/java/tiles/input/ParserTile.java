package tiles.input;

import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.TilingError;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserTile {

    public static class Delimiter extends GeneratorTile<DelimiterParser> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        GeneratorTileInput<String> delimiterInput = new GeneratorTileInput<>(
                "delimiter", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
        );
        GeneratorTileInput<Integer> partInput = new GeneratorTileInput<>(
                "part", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
        );

        @Override
        public DelimiterParser applyTile(BEASTState beastState) {
            return new DelimiterParser(this.delimiterInput.apply(beastState), this.partInput.apply(beastState));
        }

    }

    public static class Regex extends GeneratorTile<RegexParser> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        GeneratorTileInput<String> regexInput = new GeneratorTileInput<>("regex");

        @Override
        public RegexParser applyTile(BEASTState beastState) {
            return new RegexParser(this.regexInput.apply(beastState));
        }

    }

    public sealed interface Parser {
        String parse(String raw);
    }

    public static final class DelimiterParser implements Parser {

        private final String delimiter;
        private final Integer part;

        public DelimiterParser(String delimiter, Integer part) {
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
                throw new TilingError("Regex cannot be matched for input '" + raw + " '.", "Is the regex correct?");
            }
        }
    }

}