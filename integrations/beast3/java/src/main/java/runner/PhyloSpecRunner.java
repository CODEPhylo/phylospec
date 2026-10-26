package runner;

import beast.base.inference.*;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;
import org.xml.sax.SAXException;

/// Top-level entry point for running a PhyloSpec model against BEAST 3.
///
/// Orchestrates the full pipeline: lexing and parsing the source, simplifying the AST,
/// resolving variables and types, applying tiles to build a BEAST object graph, assembling
/// the MCMC run (state, distributions, operators, loggers), and finally executing it.
public class PhyloSpecRunner {

    private final String source;
    private final Optional<List<String>> tileLibraryIds;
    private final Path sourceDirectory;
    private final Path sourceFile;

    /**
     * Constructs a runner for the given PhyloSpec source code.
     */
    public PhyloSpecRunner(String source) {
        this.source = source;
        this.tileLibraryIds = Optional.empty();
        this.sourceDirectory = Path.of("").toAbsolutePath().normalize();
        this.sourceFile = null;
    }

    /**
     * Constructs a runner using only the named Tile libraries. Library identifiers are ordered
     * from most to least preferred, so a selected package can override an equivalent base mapping.
     */
    public PhyloSpecRunner(String source, List<String> tileLibraryIds) {
        this.source = source;
        this.tileLibraryIds = Optional.of(List.copyOf(tileLibraryIds));
        this.sourceDirectory = Path.of("").toAbsolutePath().normalize();
        this.sourceFile = null;
    }

    /** Constructs a runner from a source file, resolving relative model inputs beside that file. */
    public PhyloSpecRunner(Path sourceFile, List<String> tileLibraryIds) throws IOException {
        Path absoluteSource = sourceFile.toAbsolutePath().normalize();
        this.source = Files.readString(absoluteSource, StandardCharsets.UTF_8);
        this.tileLibraryIds = Optional.of(List.copyOf(tileLibraryIds));
        this.sourceDirectory = absoluteSource.getParent();
        this.sourceFile = absoluteSource;
    }

    /**
     * Checks syntax, static types and available BEAST mappings without applying tiles or running
     * MCMC. This does not validate all data or native initialization. Adapter registration and
     * existing type-property inference still take place.
     *
     * @throws PhyloSpecException if a source or mapping error is detected
     */
    public CheckedModel check() throws IOException {
        return check(warning -> {});
    }

    private CheckedModel check(Consumer<PhyloSpecDiagnostic> warningHandler) throws IOException {
        TileCatalog<BEASTState> catalog = tileLibraryIds.isPresent()
                ? TileCatalog.select(BEASTState.class, tileLibraryIds.get())
                : TileCatalog.discover(BEASTState.class);
        Diagnostics diagnostics = new Diagnostics(warningHandler);

        // run lexer

        Lexer lexer = new Lexer(this.source);
        diagnostics.stage = PhyloSpecDiagnostic.Stage.LEXING;
        lexer.registerEventListener(diagnostics);
        List<Token> tokens = lexer.scanTokens();

        // run parser

        Parser parser = new Parser(tokens);
        diagnostics.stage = PhyloSpecDiagnostic.Stage.PARSING;
        parser.registerEventListener(diagnostics);
        List<Stmt> statements = parser.parse();

        // simplify graph

        diagnostics.stage = PhyloSpecDiagnostic.Stage.SIMPLIFICATION;
        try {
            statements = new RemoveGroupings().transform(statements);
            statements = new EvaluateLiterals().transform(statements);
            statements = new EvaluateScalarFunctions().transform(statements);
        } catch (ArithmeticException | ClassCastException error) {
            throw diagnostics.failure(
                    new Error(
                            null,
                            "Could not evaluate a constant expression.",
                            "Check the operand types and values in constant expressions."),
                    error);
        }

        // run variable resolver

        VariableResolver variableResolver = new VariableResolver(statements);

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(catalog.getComponentResolver(), new Workspace(sourceDirectory));
        diagnostics.stage = PhyloSpecDiagnostic.Stage.TYPE_CHECKING;
        typeResolver.registerEventListener(diagnostics);

        try {
            typeResolver.visitStatements(statements);
        } catch (TypeError error) {
            throw diagnostics.failure(error.toError(sourceRange(parser, error.getAstNode())), error);
        }

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        // perform tiling

        EvaluateTiles<BEASTState> applyTiles =
                new EvaluateTiles<>(catalog.getTiles(), variableResolver, stochasticityResolver);
        diagnostics.stage = PhyloSpecDiagnostic.Stage.MAPPING;
        try {
            applyTiles.getBestTiling(statements);
        } catch (TileApplicationError error) {
            throw diagnostics.failure(error.toError(sourceRange(parser, error.getAstNode())), error);
        }

        return new CheckedModel(statements, parser, catalog, applyTiles, diagnostics.warnings);
    }

    /**
     * Runs the full pipeline using the same checks as {@link #check()}. Source failures are thrown
     * to the caller; use the overload with a warning handler to receive nonfatal diagnostics.
     */
    public void runPhyloSpec(String runName) throws IOException, ParserConfigurationException, SAXException {
        runPhyloSpec(runName, warning -> {});
    }

    /** Runs the model, delivering warnings without choosing an output stream for the caller. */
    public void runPhyloSpec(String runName, Consumer<PhyloSpecDiagnostic> warningHandler)
            throws IOException, ParserConfigurationException, SAXException {
        CheckedModel checked = check(warningHandler);
        seedRandomizer(checked.statements);
        BEASTState beastState = new BEASTState(runName, sourceDirectory);
        try {
            beastState = checked.tiles.applyBestTiling(beastState);
        } catch (TileApplicationError error) {
            PhyloSpecDiagnostic diagnostic = new PhyloSpecDiagnostic(
                    PhyloSpecDiagnostic.Stage.MODEL_BUILDING,
                    error.toError(sourceRange(checked.parser, error.getAstNode())),
                    false,
                    source,
                    sourceFile);
            throw new PhyloSpecException(diagnostic, error, checked.warnings);
        }

        checked.catalog.configureState(beastState);

        // add state

        State state = new State();
        beastState.setInput(state, state.stateNodeInput, new ArrayList<>(beastState.stateNodes.keySet()));

        // add distribution

        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(beastState.getAvailableID("prior"));
        beastState.setInput(prior, prior.pDistributions, new ArrayList<>(beastState.priorDistributions.values()));

        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(beastState.getAvailableID("likelihood"));
        beastState.setInput(likelihood, likelihood.pDistributions, beastState.likelihoodDistributions);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(beastState.getAvailableID("posterior"));
        beastState.setInput(posterior, posterior.pDistributions, List.of(prior, likelihood));

        // build loggers

        List<Logger> loggers = beastState.buildLoggers(posterior, prior, likelihood);

        // create MCMC object

        MCMC mcmc = new MCMC();
        beastState.setInput(mcmc, mcmc.chainLengthInput, beastState.chainLength);
        beastState.setInput(mcmc, mcmc.startStateInput, state);
        beastState.setInput(mcmc, mcmc.posteriorInput, posterior);

        beastState.setInput(mcmc, mcmc.operatorsInput, beastState.operators);
        beastState.setInput(mcmc, mcmc.loggersInput, loggers);
        mcmc.setStateFile(beastState.outputPrefix + ".state.xml", false);

        // run

        if (beastState.randomSeed != null) {
            Randomizer.setSeed(beastState.randomSeed);
        }
        beastState.initializeBEASTObjects();

        mcmc.run();
    }

    /** Seeds model construction as well as the MCMC itself when a constant seed is supplied. */
    private static void seedRandomizer(List<Stmt> statements) {
        for (Stmt statement : statements) {
            if (statement instanceof Stmt.Assignment assignment
                    && Stmt.Block.MCMC.equals(assignment.block)
                    && assignment.name.equals("randomSeed")
                    && assignment.expression instanceof Expr.Literal literal
                    && literal.value instanceof Number number
                    && number.longValue() >= 0) {
                Randomizer.setSeed(number.longValue());
            }
        }
    }

    private static Range sourceRange(Parser parser, AstNode node) {
        if (node == null) return null;
        Range range = parser.getRangeForAstNode(node);
        return range == null ? node.getRange() : range;
    }

    /**
     * A successful static check. The matched model is kept private so execution can continue from
     * this exact catalog and tiling without exposing mutable AST or native state to callers.
     */
    public static final class CheckedModel {
        private final List<Stmt> statements;
        private final Parser parser;
        private final TileCatalog<BEASTState> catalog;
        private final EvaluateTiles<BEASTState> tiles;
        private final List<PhyloSpecDiagnostic> warnings;

        private CheckedModel(
                List<Stmt> statements,
                Parser parser,
                TileCatalog<BEASTState> catalog,
                EvaluateTiles<BEASTState> tiles,
                List<PhyloSpecDiagnostic> warnings) {
            this.statements = statements;
            this.parser = parser;
            this.catalog = catalog;
            this.tiles = tiles;
            this.warnings = List.copyOf(warnings);
        }

        public List<PhyloSpecDiagnostic> warnings() {
            return warnings;
        }
    }

    /** Each check owns its listener so warnings and stages cannot leak into the next call. */
    private final class Diagnostics implements ErrorEventListener {
        private final List<PhyloSpecDiagnostic> warnings = new ArrayList<>();
        private final Consumer<PhyloSpecDiagnostic> warningHandler;
        private PhyloSpecDiagnostic.Stage stage;

        private Diagnostics(Consumer<PhyloSpecDiagnostic> warningHandler) {
            this.warningHandler = Objects.requireNonNull(warningHandler, "warningHandler");
        }

        private PhyloSpecException failure(Error error, Throwable cause) {
            return new PhyloSpecException(
                    new PhyloSpecDiagnostic(stage, error, false, source, sourceFile), cause, warnings);
        }

        @Override
        public void errorDetected(Error error) {
            // Throwing here also prevents parser recovery from accepting a partial script.
            throw failure(error, error);
        }

        @Override
        public void warningDetected(Error warning) {
            PhyloSpecDiagnostic diagnostic = new PhyloSpecDiagnostic(stage, warning, true, source, sourceFile);
            warnings.add(diagnostic);
            warningHandler.accept(diagnostic);
        }
    }
}
