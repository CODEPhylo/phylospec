package org.phylospec;

import java.util.Random;

public class FuzzingUtils {
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
}
