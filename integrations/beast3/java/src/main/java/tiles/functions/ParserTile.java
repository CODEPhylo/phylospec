package tiles.functions;

import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;
import tiling.TilingError;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserTile {

    public static class Delimiter extends GeneratorTile<DelimiterParser> {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "parse";
        }

        TileInput<String> delimiterInput = new TileInput<>("delimiter");
        TileInput<Integer> partInput = new TileInput<>("part");

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

        TileInput<String> regexInput = new TileInput<>("regex");

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
            return raw.split(Pattern.quote(this.delimiter))[this.part];
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