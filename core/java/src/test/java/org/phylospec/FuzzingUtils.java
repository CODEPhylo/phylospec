package org.phylospec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FuzzingUtils {
    // matches either a run of whitespace, a run of word characters, or a single
    // other character, so that concatenating all matches reproduces the input exactly
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\s+|\\w+|[^\\w\\s]");

    public static String randomString(Random r, int length, int minChar, int maxChar) {
        StringBuilder sb = new StringBuilder(length);
        int range = maxChar - minChar + 1;
        for (int i = 0; i < length; i++) {
            sb.append((char) (minChar + r.nextInt(range)));
        }
        return sb.toString();
    }

    public static String randomDigitHeavyString(Random r, int length) {
        // mix of digits, dots, +/- and occasional letters to stress number tokenisation
        String chars = "0123456789.+-eE ";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static String pickValidSnippet(Random r) {
        String[] snippets = {
            "foo = 1.5\n",
            "import bar\n",
            "for x in [1, 2, 3]\n",
            "\"hello world\"\n",
            "a + b * c\n",
            "x != y == z\n",
            "true false\n",
            "fn(a, b)\n",
            "// comment\nx = 42\n",
            "x >= 0.0\n",
        };
        return snippets[r.nextInt(snippets.length)];
    }

    // randomly flips, inserts, or deletes characters in a string
    public static String mutate(Random r, String input, int mutations) {
        StringBuilder sb = new StringBuilder(input);
        for (int i = 0; i < mutations; i++) {
            if (sb.isEmpty()) break;
            int op = r.nextInt(3);
            int pos = r.nextInt(sb.length());
            if (op == 0) {
                // flip a character to a random printable ASCII value
                sb.setCharAt(pos, (char) (32 + r.nextInt(95)));
            } else if (op == 1 && sb.length() > 1) {
                // delete a character
                sb.deleteCharAt(pos);
            } else {
                // insert a random printable character
                sb.insert(pos, (char) (32 + r.nextInt(95)));
            }
        }
        return sb.toString();
    }

    /* token-level mutations, using a lightweight regex tokeniser */

    // splits a string into whitespace and non-whitespace pieces, in order, so that
    // joining the returned list reproduces the original string exactly
    public static List<String> splitTokens(String input) {
        List<String> pieces = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(input);
        while (m.find()) {
            pieces.add(m.group());
        }
        return pieces;
    }

    // rejoins the output of splitTokens back into a single string
    public static String joinTokens(List<String> pieces) {
        StringBuilder sb = new StringBuilder();
        for (String piece : pieces) {
            sb.append(piece);
        }
        return sb.toString();
    }

    // returns the indices of the non-whitespace pieces in a tokenised string
    private static List<Integer> nonWhitespaceIndices(List<String> pieces) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            if (!pieces.get(i).isBlank()) {
                indices.add(i);
            }
        }
        return indices;
    }

    // deletes one random non-whitespace token, leaving surrounding whitespace untouched
    public static String deleteRandomToken(Random r, String input) {
        List<String> pieces = splitTokens(input);
        List<Integer> indices = nonWhitespaceIndices(pieces);
        if (indices.isEmpty()) return input;

        pieces.remove((int) indices.get(r.nextInt(indices.size())));
        return joinTokens(pieces);
    }

    // swaps two random non-whitespace tokens, keeping whitespace runs in place
    public static String swapRandomTokens(Random r, String input) {
        List<String> pieces = splitTokens(input);
        List<Integer> indices = nonWhitespaceIndices(pieces);
        if (indices.size() < 2) return input;

        int a = indices.get(r.nextInt(indices.size()));
        int b = indices.get(r.nextInt(indices.size()));
        while (b == a) {
            b = indices.get(r.nextInt(indices.size()));
        }

        String tmp = pieces.get(a);
        pieces.set(a, pieces.get(b));
        pieces.set(b, tmp);
        return joinTokens(pieces);
    }

    /* word-level mutations, simpler and lossy with respect to original whitespace */

    // deletes one random whitespace-delimited word
    public static String deleteRandomWord(Random r, String input) {
        List<String> words = new ArrayList<>(Arrays.asList(input.trim().split("\\s+")));
        if (words.isEmpty() || words.get(0).isEmpty()) return input;

        words.remove(r.nextInt(words.size()));
        return String.join(" ", words);
    }

    // swaps two random whitespace-delimited words
    public static String swapRandomWords(Random r, String input) {
        String[] words = input.trim().split("\\s+");
        if (words.length < 2) return input;

        int a = r.nextInt(words.length);
        int b = r.nextInt(words.length);
        while (b == a) {
            b = r.nextInt(words.length);
        }

        String tmp = words[a];
        words[a] = words[b];
        words[b] = tmp;
        return String.join(" ", words);
    }
}
