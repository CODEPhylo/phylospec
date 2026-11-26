package org.phylospec;

import java.util.ArrayList;
import java.util.List;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Diagnostic;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.workspace.Workspace;

/**
 * Runs the lexer, parser and type checker over a PhyloSpec script and collects
 * every error and warning they report, rather than stopping at the first one.
 *
 * <p>This mirrors the static analysis the language server runs, so the command
 * line reports the same problems an editor shows.
 */
public class Validator implements ErrorEventListener {
    private final ComponentResolver componentResolver;
    private final Workspace workspace;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /**
     * @param componentResolver the components the script may use
     * @param workspace         the folders used to resolve files referenced by the script
     */
    public Validator(ComponentResolver componentResolver, Workspace workspace) {
        this.componentResolver = componentResolver;
        this.workspace = workspace;
    }

    /** Validates the given source and returns all diagnostics, in the order they were found. */
    public List<Diagnostic> validate(String source) {
        diagnostics.clear();

        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(this);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        parser.registerEventListener(this);
        List<Stmt> statements = parser.parse();

        TypeResolver typeResolver = new TypeResolver(componentResolver, workspace);
        typeResolver.registerEventListener(this);
        for (Stmt statement : statements) {
            try {
                statement.accept(typeResolver);
            } catch (TypeError error) {
                Range range = error.getAstNode() == null ? null : parser.getRangeForAstNode(error.getAstNode());
                if (range == null) range = parser.getRangeForAstNode(statement);
                errorDetected(error.toError(range));
            }
        }

        return List.copyOf(diagnostics);
    }

    @Override
    public void errorDetected(Error error) {
        diagnostics.add(Diagnostic.of(error, Diagnostic.Severity.ERROR));
    }

    @Override
    public void warningDetected(Error warning) {
        diagnostics.add(Diagnostic.of(warning, Diagnostic.Severity.WARNING));
    }
}
