package org.phylospec.engines;

import java.util.*;
import java.util.stream.Stream;
import org.phylospec.ast.ArgumentResolutionError;
import org.phylospec.ast.AstVisitor;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.*;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;

/**
 * Answers whether a set of engines supports a model, a call, or a generator.
 * When no engine is loaded, everything is always supported.
 */
public final class EngineSupport {

    private final Map<String, List<Generator__1>> implementedGenerators = new LinkedHashMap<>();
    boolean noEngineLoaded;

    public EngineSupport(List<EngineSpecificationSchema> engines) {
        noEngineLoaded = engines.isEmpty();

        for (EngineSpecificationSchema engine : engines) {
            for (Generator__1 generator : engine.getGenerators()) {
                implementedGenerators
                        .computeIfAbsent(getUnqualifiedName(generator.getName()), name -> new ArrayList<>())
                        .add(generator);
            }
        }
    }

    public EngineSupport(EngineSpecificationSchema... engines) {
        this(List.of(engines));
    }

    /* support methods */

    /**
     * Returns how well the engines implement every generator the given model calls.
     */
    public ModelSupport supports(List<Stmt> model) {
        // init StochasticityResolver

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        model.forEach(stmt -> stmt.accept(stochasticityResolver));

        // collect calls

        List<Expr.Call> calls = new ArrayList<>();

        AstVisitor<Void, Void, Void> callCollector = new AstVisitor<>() {
            @Override
            public Void visitCall(Expr.Call expr) {
                calls.add(expr);
                return AstVisitor.super.visitCall(expr);
            }
        };
        model.forEach(stmt -> stmt.accept(callCollector));

        // check support for each call

        return new ModelSupport(calls.stream()
                .map(call -> supports(call, stochasticityResolver))
                .toList());
    }

    /**
     * Returns how well the engines implement the given generator. Full support means an engine
     * offers every declared argument, including the ones that are not required, as any model may
     * use them. Stochasticities are ignored.
     */
    public GeneratorSupport supports(Generator generator) {
        // our strategy is to simulate an Expr.Call object with all possible generator arguments used
        // and then check if that call is supported

        List<String> generatorArguments =
                generator.getArguments().stream().map(Argument::getName).toList();

        String qualifiedGeneratorName = generator.getNamespace() == null
                ? generator.getName()
                : generator.getNamespace() + "." + generator.getName();

        Expr.Call call = new Expr.Call(
                qualifiedGeneratorName,
                generatorArguments.stream()
                        .map(name -> new Expr.AssignedArgument(name, new Expr.Variable(name)))
                        .toArray(Expr.Argument[]::new));

        CallSupport callSupport = supports(call);

        // derive the generator argument support

        Map<String, Boolean> argumentSupport = new LinkedHashMap<>();
        for (int i = 0; i < generatorArguments.size(); i++) {
            argumentSupport.put(
                    generatorArguments.get(i), callSupport.argumentSupport().get(i));
        }

        return new GeneratorSupport(callSupport.isFullySupported(), argumentSupport);
    }

    /**
     * Returns how well the engines supports the given call statement.
     * The stochasticity of the arguments is not taken into account, as we cannot always infer them based on the call alone.
     */
    public CallSupport supports(Expr.Call call) {
        return supports(call, null);
    }

    /**
     * Returns how well the engines supports the given call statement, including the stochasticity the given resolver assigns to them.
     */
    public CallSupport supports(Expr.Call call, StochasticityResolver stochasticityResolver) {
        if (noEngineLoaded) {
            // no engines are loaded
            // we always return full support
            return new CallSupport(call, true, Collections.nCopies(call.arguments.length, true));
        }

        // check if there is an implementation that supports the call

        List<Generator__1> candidates = getCandidateImplementations(call.functionName);
        boolean isFullySupported =
                candidates.stream().anyMatch(candidate -> supports(candidate, call, stochasticityResolver));

        // if no engine supports the call, we report which of the passed arguments an engine offers
        // at all, so that tooling can point at the ones to blame
        List<Boolean> argumentSupport = Arrays.stream(call.arguments)
                .map(argument -> isFullySupported || isOffered(candidates, argument, stochasticityResolver))
                .toList();

        return new CallSupport(call, isFullySupported, argumentSupport);
    }

    /**
     * Checks if the arguments in the call matches the arguments the implemented generator offers.
     */
    private static boolean supports(
            Generator__1 implementedGenerator, Expr.Call call, StochasticityResolver stochasticityResolver) {
        // collect the parameters for the generator implementation

        Map<String, Argument__1> declaredArguments = new LinkedHashMap<>();
        List<Expr.Call.Parameter> parameters = new ArrayList<>();
        for (Argument__1 argument : implementedGenerator.getArguments()) {
            declaredArguments.put(argument.getName(), argument);
            parameters.add(new Expr.Call.Parameter(argument.getName(), Boolean.TRUE.equals(argument.getRequired())));
        }

        // try to resolve the arguments passed to the call

        Map<String, Expr.Argument> boundArguments;
        try {
            boundArguments = call.resolveArgumentNames(parameters);
        } catch (ArgumentResolutionError error) {
            // this engine cannot take the call in this shape
            return false;
        }

        // check the stochasticities of the bound arguments

        return boundArguments.entrySet().stream()
                .allMatch(bound -> acceptsStochasticity(
                        declaredArguments.get(bound.getKey()), bound.getValue(), stochasticityResolver));
    }

    /**
     * Checks whether the engine can take the given passed argument for the argument it declares.
     */
    private static boolean acceptsStochasticity(
            Argument__1 declaredArgument, Expr.Argument passedArgument, StochasticityResolver stochasticityResolver) {
        // without a resolver we know nothing about the stochasticity and do not hold it against the engine
        if (stochasticityResolver == null) return true;

        return Boolean.TRUE.equals(declaredArgument.getCanBeStochastic())
                || (stochasticityResolver.getStochasticity(passedArgument) != Stochasticity.STOCHASTIC);
    }

    /* shape matching helpers */

    /**
     * Returns the implemented generators for the given name and namespace. The namespace is only taken into account
     * if both sides specify one, as engine specifications generally leave it out.
     */
    private List<Generator__1> getCandidateImplementations(String name) {
        int lastPeriod = name.lastIndexOf('.');
        String namespace = lastPeriod == -1 ? null : name.substring(0, lastPeriod);

        return implementedGenerators.getOrDefault(getUnqualifiedName(name), List.of()).stream()
                .filter(generator -> generator.getNamespace() == null
                        || namespace == null
                        || generator.getNamespace().equals(namespace))
                .toList();
    }

    /**
     * Checks if any candidate offers the argument the given passed argument names.
     */
    private static boolean isOffered(
            List<Generator__1> candidates, Expr.Argument passedArgument, StochasticityResolver stochasticityResolver) {
        String argumentName = passedArgument.name != null
                ? passedArgument.name
                : passedArgument.expression instanceof Expr.Variable variable ? variable.variableName : null;

        // an argument that is passed positionally names no argument of its own
        // if there was a generator with exactly one required argument, we would
        // not call this function
        if (argumentName == null) return false;

        // an argument an engine offers but cannot take as a random variable is not offered for
        // this call, so that tooling blames the argument that actually keeps the engine out
        return candidates.stream()
                .flatMap(candidate -> candidate.getArguments().stream())
                .anyMatch(argument -> argument.getName().equals(argumentName)
                        && acceptsStochasticity(argument, passedArgument, stochasticityResolver));
    }

    /* helper functions for names */

    /**
     * Returns the name without its namespace.
     */
    private static String getUnqualifiedName(String name) {
        int lastPeriod = name.lastIndexOf('.');
        if (lastPeriod == -1) return name;
        return name.substring(lastPeriod + 1);
    }

    /* support classes */

    /** How much of something the engines implement. */
    public enum Support {
        FULL_SUPPORT,
        PARTIAL_SUPPORT,
        NO_SUPPORT;

        private static Support of(boolean isFullySupported, Stream<Boolean> supportedParts) {
            if (isFullySupported) return FULL_SUPPORT;
            return supportedParts.anyMatch(part -> part) ? PARTIAL_SUPPORT : NO_SUPPORT;
        }
    }

    public record ModelSupport(List<CallSupport> callSupport) {

        public boolean isFullySupported() {
            return callSupport.stream().allMatch(CallSupport::isFullySupported);
        }

        public Support support() {
            return Support.of(
                    isFullySupported(), callSupport.stream().map(call -> call.support() != Support.NO_SUPPORT));
        }
    }

    public record CallSupport(Expr.Call call, boolean isFullySupported, List<Boolean> argumentSupport) {

        public Support support() {
            return Support.of(isFullySupported, argumentSupport.stream());
        }
    }

    public record GeneratorSupport(boolean isFullySupported, Map<String, Boolean> argumentSupport) {

        public Support support() {
            return Support.of(isFullySupported, argumentSupport.values().stream());
        }
    }
}
