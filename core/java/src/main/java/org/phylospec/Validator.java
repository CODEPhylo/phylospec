package org.phylospec;

import java.util.ArrayList;
import java.util.List;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.engines.EngineSupport;
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
 * If engines are given, it also warns about parts of the model those engines
 * cannot run.
 *
 * <p>This is the single static-analysis pipeline shared by the command line
 * tools and the language server, so both report the same problems.
 */
public class Validator {
    private final ComponentResolver componentResolver;
    private final Workspace workspace;
    private final List<EngineSpecificationSchema> engines;

    /**
     * The outcome of validating a script: the diagnostics, plus the intermediate
     * results that tools such as the language server use for hover and completion.
     *
     * @param tokens       the lexed tokens
     * @param parser       the parser, which maps AST nodes and tokens to source ranges
     * @param statements   the parsed statements
     * @param typeResolver the type resolver after visiting every statement
     * @param diagnostics  every error and warning, in the order they were found
     */
    public record Result(
            List<Token> tokens,
            Parser parser,
            List<Stmt> statements,
            TypeResolver typeResolver,
            List<Diagnostic> diagnostics) {}

    /**
     * @param componentResolver the components the script may use
     * @param workspace         the folders used to resolve files referenced by the script
     */
    public Validator(ComponentResolver componentResolver, Workspace workspace) {
        this(componentResolver, workspace, List.of());
    }

    /**
     * @param componentResolver the components the script may use
     * @param workspace         the folders used to resolve files referenced by the script
     * @param engines           the engines the model should run on; an empty list skips the
     *                          engine-support check
     */
    public Validator(
            ComponentResolver componentResolver, Workspace workspace, List<EngineSpecificationSchema> engines) {
        this.componentResolver = componentResolver;
        this.workspace = workspace;
        this.engines = List.copyOf(engines);
    }

    /** Validates the given source. */
    public Result validate(String source) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        ErrorEventListener listener = new ErrorEventListener() {
            @Override
            public void errorDetected(Error error) {
                diagnostics.add(Diagnostic.of(error, Diagnostic.Severity.ERROR));
            }

            @Override
            public void warningDetected(Error warning) {
                diagnostics.add(Diagnostic.of(warning, Diagnostic.Severity.WARNING));
            }
        };

        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(listener);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        parser.registerEventListener(listener);
        List<Stmt> statements = parser.parse();

        TypeResolver typeResolver = new TypeResolver(componentResolver, workspace);
        typeResolver.registerEventListener(listener);
        for (Stmt statement : statements) {
            try {
                statement.accept(typeResolver);
            } catch (TypeError error) {
                Range range = error.getAstNode() == null ? null : parser.getRangeForAstNode(error.getAstNode());
                if (range == null) range = parser.getRangeForAstNode(statement);
                listener.errorDetected(error.toError(range));
            }
        }

        if (!engines.isEmpty()) {
            new EngineSupport(engines).supports(statements).getWarnings().forEach(listener::warningDetected);
        }

        return new Result(tokens, parser, statements, typeResolver, List.copyOf(diagnostics));
    }
}
