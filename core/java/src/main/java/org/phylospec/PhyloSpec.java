package org.phylospec;

import org.phylospec.ast.AstPrinter;
import org.phylospec.ast.Stmt;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * This class can be used to run the PhyloSpec parser.
 * <br>
 * You can either pass the path to a PhyloSpec script, or
 * you can pass no argument and use the REPL to interact
 * with the language.
 * <br>
 * Note that there is no interpreter so far, only a parser.
 * The output of this script is the printed AST tree.
 */
public class PhyloSpec {
    private static boolean hadError;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: phylospec [script file]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError) System.exit(65);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
        }
    }

    private static void run(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // stop if there was a syntax error
        if (hadError) return;

        AstPrinter printer = new AstPrinter();
        for (Stmt statement : statements) {
            System.out.println(statement.accept(printer));
        }
    }

    /** Report an error on a specific line but not directly connected
     * to a specified token. */
    public static void error(int line, String message) {
        report(line, "", message);
    }

    /** Report an error related to specific token. */
    public static void error(Token token, String message) {
        report(token.line, "", message);
    }

    private static void report(int line, String where, String message) {
        System.err.println(
                "[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
