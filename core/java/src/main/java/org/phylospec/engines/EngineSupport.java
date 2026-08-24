package org.phylospec.engines;

import java.util.*;
import java.util.stream.Stream;
import org.phylospec.ast.ArgumentResolutionError;
import org.phylospec.ast.AstVisitor;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.*;

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
        List<Expr.Call> calls = new ArrayList<>();

        AstVisitor<Void, Void, Void> callCollector = new AstVisitor<>() {
            @Override
            public Void visitCall(Expr.Call expr) {
                calls.add(expr);
                return AstVisitor.super.visitCall(expr);
            }
        };
        model.forEach(stmt -> stmt.accept(callCollector));

        return new ModelSupport(calls.stream().map(this::supports).toList());
    }

    /**
     * Returns how well the engines implement the given generator. Full support means an engine
     * offers every declared argument, including the ones that are not required, as any model may
     * use them.
     */
    public GeneratorSupport supports(Generator generator) {
        List<String> declaredArguments =
                generator.getArguments().stream().map(Argument::getName).toList();

        // the call carries the namespace in its name, as that is where a call keeps it
        String qualifiedName = generator.getNamespace() == null
                ? generator.getName()
                : generator.getNamespace() + "." + generator.getName();

        // an engine implements a generator exactly if it can run a call that passes every declared
        // argument, so we simulate a call to decide this
        CallSupport callSupport = supports(new Expr.Call(
                qualifiedName,
                declaredArguments.stream()
                        .map(name -> new Expr.AssignedArgument(name, new Expr.Variable(name)))
                        .toArray(Expr.Argument[]::new)));

        // derive the generator argument support
        Map<String, Boolean> argumentSupport = new LinkedHashMap<>();
        for (int i = 0; i < declaredArguments.size(); i++) {
            argumentSupport.put(
                    declaredArguments.get(i), callSupport.argumentSupport().get(i));
        }

        return new GeneratorSupport(callSupport.isFullySupported(), argumentSupport);
    }

    /**
     * Returns how well the engines implement the generator called by the given call, with the
     * arguments the call actually passes.
     */
    public CallSupport supports(Expr.Call call) {
        if (noEngineLoaded) {
            // no engines are loaded
            // we always return full support
            return new CallSupport(call, true, Collections.nCopies(call.arguments.length, true));
        }

        List<Generator__1> candidates = getCandidateImplementations(call.functionName);

        boolean isFullySupported = candidates.stream().anyMatch(candidate -> supports(candidate, call));

        // where no engine takes the call we report which of the passed arguments an engine offers
        // at all, so that tooling can point at the ones to blame
        List<Boolean> argumentSupport = Arrays.stream(call.arguments)
                .map(argument -> isFullySupported || isOffered(candidates, argument))
                .toList();

        return new CallSupport(call, isFullySupported, argumentSupport);
    }

    /**
     * Checks if the arguments in the call matches the arguments the implemented generator offers.
     */
    private static boolean supports(Generator__1 implementedGenerator, Expr.Call call) {
        // collect the parameters for the genrator implementation

        List<Expr.Call.Parameter> parameters = new ArrayList<>();
        for (Argument__1 argument : implementedGenerator.getArguments()) {
            parameters.add(new Expr.Call.Parameter(argument.getName(), Boolean.TRUE.equals(argument.getRequired())));
        }

        // try to resolve the arguments passed to the call

        try {
            call.resolveArgumentNames(parameters);
            return true;
        } catch (ArgumentResolutionError error) {
            // this engine cannot take the call in this shape
            return false;
        }
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
    private static boolean isOffered(List<Generator__1> candidates, Expr.Argument passedArgument) {
        String argumentName = passedArgument.name != null
                ? passedArgument.name
                : passedArgument.expression instanceof Expr.Variable variable ? variable.variableName : null;

        // an argument that is passed positionally names no argument of its own
        // if there was a generator with exactly one required argument, we would
        // not call this function
        if (argumentName == null) return false;

        return candidates.stream()
                .flatMap(candidate -> candidate.getArguments().stream())
                .anyMatch(argument -> argument.getName().equals(argumentName));
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

        /** Derives the support of a whole from whether it runs and from which of its parts are supported. */
        private static Support of(boolean isFullySupported, Stream<Boolean> supportedParts) {
            if (isFullySupported) return FULL_SUPPORT;
            return supportedParts.anyMatch(part -> part) ? PARTIAL_SUPPORT : NO_SUPPORT;
        }
    }

    /**
     * How well the engines implement a model, along with the support of every call it makes. The
     * calls are kept in a list rather than in a map because two calls of the same model can be
     * equal to each other.
     */
    public record ModelSupport(List<CallSupport> callSupport) {

        /** Returns whether the engines can run every call of the model. */
        public boolean isFullySupported() {
            return callSupport.stream().allMatch(CallSupport::isFullySupported);
        }

        /** Returns the support of the model as a whole, which is partial as soon as any call is supported at all. */
        public Support support() {
            return Support.of(
                    isFullySupported(), callSupport.stream().map(call -> call.support() != Support.NO_SUPPORT));
        }
    }

    /**
     * How well the engines implement a call. The support of the passed arguments is given in the
     * order of {@link Expr.Call#arguments}, as two arguments of the same call can be equal to each
     * other and could not be told apart in a map.
     */
    public record CallSupport(Expr.Call call, boolean isFullySupported, List<Boolean> argumentSupport) {

        /** Returns the support of the call as a whole, which is partial as soon as any passed argument is offered. */
        public Support support() {
            return Support.of(isFullySupported, argumentSupport.stream());
        }
    }

    /**
     * How well the engines implement a generator, along with the support of each of its declared
     * arguments by name.
     */
    public record GeneratorSupport(boolean isFullySupported, Map<String, Boolean> argumentSupport) {

        /** Returns the support of the generator as a whole, which is partial as soon as any argument is offered. */
        public Support support() {
            return Support.of(isFullySupported, argumentSupport.values().stream());
        }
    }
}
