package org.phylospec.converters;

import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ConvertToLPhy {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new RuntimeException("Provide a .phylospec file name to be converted into an LPhy script.");
        }

        String phylospecFilePath = args[0];
        byte[] phylospecBytes = Files.readAllBytes(Paths.get(phylospecFilePath));
        String phylospecString = new String(phylospecBytes, Charset.defaultCharset());

        Lexer lexer = new Lexer(phylospecString);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        ComponentResolver componentResolver = new ComponentResolver();
        componentResolver.registerLibraryFromInputStream(
                ConvertToLPhy.class.getResourceAsStream("/phylospec-core-component-library.json")
        );
        componentResolver.importEntireNamespace(List.of("phylospec"));

        String lphyString = LPhyConverter.convertToLPhy(statements, componentResolver);

        System.out.println(lphyString);
    }
}
