package org.phylospec.engines;

import java.util.*;
import java.util.stream.Collectors;
import org.phylospec.ast.AstVisitor;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.*;

/**
 * Answers whether a set of engines can run something.
 * <p>
 * A component library says what a component <em>is</em>; an engine specification says which of them
 * an engine <em>implements</em>. An engine only implements a subset of a component library, so a
 * model can type-check against the library and still not be runnable. This class decides that
 * question for a {@link Generator}, for a single {@link Expr.Call}, and for a whole {@link Stmt}.
 * <p>
 * Several engines can be given at once and something is supported if <em>any</em> of them
 * implements it, because a model may legitimately need more than one engine (a BEAST 2 package is
 * an engine in its own right).
 * <p>
 * With no engine specification given, nothing is claimed and everything is supported. This matters
 * because a repository without an `engines` directory and an unreachable repository both yield no
 * engine specifications at all, and tooling must not start rejecting every component in that case.
 * <p>
 * The matching rules are documented in `repository/shape-matching.md`.
 */
public final class EngineSupport {

    private final List<EngineSpecificationSchema> engines;

    // every generator any of the engines implements, indexed by its unqualified name
    private final Map<String, List<Generator__1>> implementedGenerators = new LinkedHashMap<>();

    private EngineSupport(List<EngineSpecificationSchema> engines) {
        this.engines = List.copyOf(engines);

        for (EngineSpecificationSchema engine : engines) {
            for (Generator__1 generator : engine.getGenerators()) {
                implementedGenerators
                        .computeIfAbsent(getUnqualifiedName(generator.getName()), name -> new ArrayList<>())
                        .add(generator);
            }
        }
    }

    /**
     * Returns the support offered by the given engines taken together.
     */
    public static EngineSupport of(List<EngineSpecificationSchema> engines) {
        return new EngineSupport(engines);
    }

    /**
     * Returns the support offered by the given engines taken together.
     */
    public static EngineSupport of(EngineSpecificationSchema... engines) {
        return new EngineSupport(List.of(engines));
    }

    /**
     * Returns whether any of the engines implements the given generator. This only returns true if the engine
     * implements all possible fields, including the non-required ones.
     */
    public GeneratorSupport supports(Generator generator) {
        for (Generator__1 implementedGenerator : getCandidates(generator.getName(), generator.getNamespace())) {
            Set<String> declaredArguments = generator.getArguments().stream()
                    .map(Argument::getName)
                    .collect(Collectors.toCollection(HashSet::new));

            if (covers(implementedGenerator, declaredArguments)) return true;
        }

        return false;
    }

    public record GeneratorSupport(
            // support: enum for FULL_SUPPORT, PARTIAL_SUPPORT, NO_SUPPORT,
            // argumentSupport: Map<String, Boolean>
            ) {}
    ;

    /**
     * Returns whether any of the engines implements every generator called in the given statement.
     */
    public ModelSupport supports(Stmt stmt) {
        // collect all calls

        List<Expr.Call> calls = new ArrayList<>();

        stmt.accept(new AstVisitor<Void, Void, Void>() {
            @Override
            public Void visitCall(Expr.Call expr) {
                calls.add(expr);
                return AstVisitor.super.visitCall(expr);
            }
        });

        // make sure all calls are supported

        return calls.stream().allMatch(this::supports);
    }

    public record ModelSupport(
            // support: enum for FULL_SUPPORT, PARTIAL_SUPPORT, NO_SUPPORT,
            // callSupport: Map<Call, Boolean>
            ) {}
    ;

    /**
     * Returns whether any of the engines implements the generator called by the given call, with
     * the arguments the call actually passes.
     */
    public CallSupport supports(Expr.Call call) {
        if (engines.isEmpty()) return true;

        for (Generator__1 implementedGenerator : getCandidates(call.functionName, getNamespace(call.functionName))) {
            for (Set<String> passedArguments : getPassedArgumentNames(implementedGenerator, call)) {
                if (covers(implementedGenerator, passedArguments)) return true;
            }
        }

        return false;
    }

    public record CallSupport(
            // support: enum for FULL_SUPPORT, PARTIAL_SUPPORT, NO_SUPPORT,
            // argumentSupport: Map<Argument, Boolean>
            ) {}
    ;

    /**
     * Returns the implemented generators that could be the one named by the given name and
     * namespace. The namespace is only taken into account if both sides specify one, as engine
     * specifications generally leave it out.
     */
    private List<Generator__1> getCandidates(String name, String namespace) {
        return implementedGenerators.getOrDefault(getUnqualifiedName(name), List.of()).stream()
                .filter(generator -> generator.getNamespace() == null
                        || namespace == null
                        || generator.getNamespace().equals(namespace))
                .toList();
    }

    /* shape matching */

    /** Checks if all used arguments are supported and if all required arguments are used. */
    private static boolean covers(Generator__1 implementedGenerator, Set<String> usedArguments) {
        Set<String> offeredArguments = new LinkedHashSet<>();
        Set<String> insistedArguments = new LinkedHashSet<>();

        for (Argument__1 argument : implementedGenerator.getArguments()) {
            offeredArguments.add(argument.getName());
            if (argument.getRequired()) insistedArguments.add(argument.getName());
        }

        return offeredArguments.containsAll(usedArguments) && usedArguments.containsAll(insistedArguments);
    }

    private static List<Set<String>> getPassedArgumentNames(Generator__1 implementedGenerator, Expr.Call call) {
        Set<String> passedArguments = new LinkedHashSet<>();

        for (int i = 0; i < call.arguments.length; i++) {
            Expr.Argument argument = call.arguments[i];

            if (argument.name != null) {
                passedArguments.add(argument.name);
            } else if (argument.expression instanceof Expr.Variable variable) {
                passedArguments.add(variable.variableName);
            } else if (i == 0) {
                // the first argument can be passed positionally. we can name it without consulting
                // the component library because the engine specifications list the arguments in the
                // order of the library, which EngineSpecGenerator enforces for the first argument
                String firstArgumentName = getFirstArgumentName(implementedGenerator);
                if (firstArgumentName == null) return List.of();
                passedArguments.add(firstArgumentName);
            } else {
                // the argument name can only be dropped for the first argument or for a variable,
                // so this call is invalid and no engine can run it
                return List.of();
            }
        }

        // a single unnamed variable argument is ambiguous. in `exp(mean)`, the variable `mean`
        // either names the argument it is assigned to, or simply fills the first argument. we
        // accept the generator if either reading works, as the type resolver does

        if (call.arguments.length == 1
                && call.arguments[0].name == null
                && call.arguments[0].expression instanceof Expr.Variable) {
            String firstArgumentName = getFirstArgumentName(implementedGenerator);
            if (firstArgumentName != null) return List.of(passedArguments, Set.of(firstArgumentName));
        }

        return List.of(passedArguments);
    }

    private static String getFirstArgumentName(Generator__1 implementedGenerator) {
        if (implementedGenerator.getArguments().isEmpty()) return null;
        return implementedGenerator.getArguments().getFirst().getName();
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

    /**
     * Returns the namespace the name is qualified with, or null if it is unqualified.
     */
    private static String getNamespace(String name) {
        int lastPeriod = name.lastIndexOf('.');
        if (lastPeriod == -1) return null;
        return name.substring(0, lastPeriod);
    }
}
