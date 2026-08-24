package org.phylospec.engines;

import java.util.ArrayList;
import java.util.List;
import org.phylospec.ast.Expr;
import org.phylospec.engines.EngineSupport.ArgumentSupport;
import org.phylospec.engines.EngineSupport.CallSupport;
import org.phylospec.engines.EngineSupport.Support;
import org.phylospec.errors.Error;
import org.phylospec.lexer.Range;

/**
 * How well a set of engines implements a model, along with the support of every call it makes.
 */
public record ModelSupport(List<String> engineNames, List<CallSupport> callSupport) {

    /** Returns whether the engines can run every call of the model. */
    public boolean isFullySupported() {
        return callSupport.stream().allMatch(CallSupport::isFullySupported);
    }

    /** Returns the support of the model as a whole, which is partial as soon as any call is supported at all. */
    public Support support() {
        return Support.of(isFullySupported(), callSupport.stream().map(call -> call.support() != Support.NO_SUPPORT));
    }

    /* warnings */

    /**
     * Returns a warning for every part of the model the engines cannot run. A model is the only
     * thing we warn about, as only a model is something the user wrote themselves.
     */
    public List<Error> getWarnings() {
        List<Error> warnings = new ArrayList<>();
        callSupport.forEach(call -> addWarnings(warnings, call));
        return warnings;
    }

    /**
     * Adds the warnings for a single call. A call no engine implements at all is blamed as a whole,
     * while a call that is only partly supported blames the arguments that are not supported, as
     * those are the ones the user can do something about.
     */
    private void addWarnings(List<Error> warnings, CallSupport callSupport) {
        if (callSupport.isFullySupported()) return;

        Expr.Call call = callSupport.call();

        if (callSupport.support() == Support.NO_SUPPORT) {
            addWarning(
                    warnings,
                    call.getRange(),
                    describeEngines() + " not implement '" + call.functionName + "'.",
                    "Use a generator one of your engines implements, or add an engine which does.");
            return;
        }

        int warningCount = warnings.size();

        for (int i = 0; i < call.arguments.length; i++) {
            ArgumentSupport argumentSupport = callSupport.argumentSupport().get(i);
            if (argumentSupport.isSupported()) continue;

            Expr.Argument argument = call.arguments[i];
            String argumentName = EngineSupport.getArgumentName(argument);
            String describedArgument = argumentName == null ? "this argument" : "'" + argumentName + "'";

            addWarning(
                    warnings,
                    argument.getRange() != null ? argument.getRange() : call.getRange(),
                    describeEngines() + " not support " + describedArgument + " for '" + call.functionName + "'"
                            + (argumentSupport == ArgumentSupport.STOCHASTICITY_UNSUPPORTED
                                    ? " as a random variable."
                                    : "."),
                    argumentSupport == ArgumentSupport.STOCHASTICITY_UNSUPPORTED
                            ? "Pass a fixed value for " + describedArgument + " instead of a random variable."
                            : "Have a look at the arguments one of your engines offers for '" + call.functionName
                                    + "'.");
        }

        // none of the passed arguments is to blame, so the call itself is what the engines turn
        // down, most likely because it leaves out an argument one of them requires
        if (warnings.size() == warningCount) {
            addWarning(
                    warnings,
                    call.getRange(),
                    describeEngines() + " not support this call of '" + call.functionName + "'.",
                    "Have a look at the arguments one of your engines requires for '" + call.functionName + "'.");
        }
    }

    /**
     * Adds a warning. A warning without a range is dropped, as there is nothing to point the user
     * at.
     */
    private static void addWarning(List<Error> warnings, Range range, String description, String hint) {
        if (range == null) return;

        warnings.add(new Error(range, description, hint));
    }

    /**
     * Returns the engines by name, followed by the negated verb that agrees with them, so that a
     * single engine reads as "beast2 does" and several as "beast2 and revbayes do".
     */
    private String describeEngines() {
        String verb = engineNames.size() == 1 ? " does" : " do";

        if (engineNames.size() <= 1) return String.join("", engineNames) + verb;

        return String.join(", ", engineNames.subList(0, engineNames.size() - 1)) + " and " + engineNames.getLast()
                + verb;
    }
}
