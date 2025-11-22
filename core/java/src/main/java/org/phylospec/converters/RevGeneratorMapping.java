package org.phylospec.converters;

import org.phylospec.ast.Expr;

import java.util.Map;

public class RevGeneratorMapping {
    /**
     * Takes a PhyloSpec generator name and an arguments map and returns the corresponding Rev expression. Returns
     * null if the generator cannot be mapped to Rev code.
     */
    static StringBuilder map(
            Expr.Call expr,
            Map<String, String> arguments,
            RevConverter converter
    ) {
        return switch (expr.functionName) {
            case "Exponential" -> build(
                    "dnExponential",
                    "lambda", arg("rate", arguments)
            );
            case "Normal" -> build(
                    "dnNormal",
                    "mean", arg("mean", arguments),
                    "sd", arg("sd", arguments)
            );
            case "LogNormal" -> build(
                    "dnLognormal",
                    "mean", arg("meanlog", arguments),
                    "sd", arg("sdlog", arguments)
            );
            case "Gamma" -> build(
                    "dnGamma",
                    "shape", arg("shape", arguments),
                    "rate", arg("rate", arguments)
            );
            case "Beta" -> build(
                    "dnBeta",
                    "alpha", arg("alpha", arguments),
                    "beta", arg("beta", arguments)
            );
            case "Uniform" -> build(
                    "dnUniform",
                    "lower", arg("lower", arguments),
                    "upper", arg("upper", arguments)
            );
            case "Dirichlet" -> build(
                    "dnDirichlet",
                    "alpha", arg("alpha", arguments)
            );
            case "FossilBirthDeath" -> build(
                    "dnFossilizedBirthDeath",
                    "originAge", arg("origin", arguments),
                    "lambda", arg("birthRate", arguments),
                    "mu", arg("deathRate", arguments),
                    "psi", arg("samplingRate", arguments),
                    "taxa", arg("taxa", arguments),
                    "rho", arg("rho", arguments)
            );
            case "BirthDeath" -> build(
                    "dnBirthDeath",
                    "lambda", arg("birthRate", arguments),
                    "mu", arg("deathRate", arguments),
                    "rootAge", arg("rootHeight", arguments),
                    "taxa", arg("taxa", arguments)
            );
            case "Coalescent" -> build(
                    "dnCoalescent",
                    "theta", arg("populationSize", arguments),
                    "taxa", arg("taxa", arguments)
            );
            case "JC69" -> build(
                    "fnJC",
                    "num_states", "4"
            );
            case "K80" -> build(
                    "fnK80",
                    "kappa",  arg("kappa", arguments)
            );
            case "F81" -> build(
                    "fnF81",
                    "baseFrequencies",  arg("baseFrequencies", arguments)
            );
            case "HKY" -> build(
                    "fnHKY",
                    "kappa",  arg("kappa", arguments),
                    "baseFrequencies",  arg("baseFrequencies", arguments)
            );
            case "GTR" -> build(
                    "fnGTR",
                    "exchangeRates",  arg("rateMatrix", arguments),
                    "baseFrequencies",  arg("baseFrequencies", arguments)
            );
            case "nexus" -> build(
                    "readDiscreteCharacterData",
                    "file",  arg("file", arguments)
            );
            case "fasta" -> build(
                    "readDiscreteCharacterData",
                    "file",  arg("file", arguments)
            );
            case "PhyloBM" -> build(
                    "dnPhyloBrownianREML",
                    "tree", arg("tree", arguments),
                    "branchRates", arg("sigma", arguments)
            );
            case "PhyloOU" -> build(
                    "dnPhyloOrnsteinUhlenbeckREML",
                    "tree", arg("tree", arguments),
                    "alpha", arg("alpha", arguments),
                    "theta", arg("optimum", arguments),
                    "sigma", arg("sigma", arguments),
                    "rootStates", arg("rootValue", arguments)
            );
            case "PhyloCTMC" -> build(
                    "dnPhyloCTMC",
                    "tree", arg("tree", arguments),
                    "Q", arg("Q", arguments),
                    "siteRates", arg("siteRates", arguments, true),
                    "branchRates", arg("branchRates", arguments, true)
            );
            case "IID" -> build(
                    "dnIID",
                    "numValues", arg("n", arguments),
                    "valueDistribution", arg("base", arguments)
            );
            case "zip" -> {
                // we have smth like zip(first=first, second=second)
                // we convert it into
                // temp_first = first
                // temp_second = second
                // for (i in 1:temp_first.size()) {
                //     temp_zipped[i][1] <- temp_first[i]
                //     temp_zipped[i][2] <- temp_second[i]
                // }

                // assign temp_first and temp_second

                StringBuilder first = new StringBuilder(arg("first", arguments));
                StringBuilder second = new StringBuilder(arg("second", arguments));

                RevStmt.Assignment firstListStmt = converter.addRevAssignment(
                        new RevStmt.Assignment("temp_first", first)
                );
                String firstListName = firstListStmt.variableName;

                RevStmt.Assignment secondListStmt = converter.addRevAssignment(
                        new RevStmt.Assignment("temp_second", second)
                );
                String secondListName = secondListStmt.variableName;

                // start for loop

                String indexVarName = converter.getNextAvailableVariableName("i");
                converter.addSimpleRevStatement("for (" + indexVarName + " in 1:" + firstListName + ".size()) {");

                // assign  temp_zipped

                RevStmt.Assignment firstExpressionStmt = converter.addRevAssignment(
                        new RevStmt.Assignment(
                                "temp_zipped", new String[] {indexVarName, "1"},
                            new StringBuilder(firstListName).append("[").append(indexVarName).append("]")
                        )
                );
                String zippedVarName = firstExpressionStmt.variableName;

                converter.addRevAssignment(
                        new RevStmt.Assignment(
                                "temp_zipped", new String[] {indexVarName, "2"},
                                new StringBuilder(secondListName).append("[").append(indexVarName).append("]")
                        ), false
                );

                // end for loop

                converter.addSimpleRevStatement("}");

                // zippedVarName is now in place of the original expression
                yield new StringBuilder(zippedVarName);
            }
            default -> throw new RevConverter.RevConversionError("Generator " + expr.functionName + " is not supported.");
        };
    }

    /** Returns the argument {@code name}. Raises an {@code RevConversionError} if the argument was not
     * provided. */
    private static String arg(String name, Map<String, String> arguments) {
        return arg(name, arguments, false);
    }
    /** Returns the argument {@code name}.
     * If the argument was not provided and {@code optional} is set to {@code false}, raises an
     * {@code RevConversionError}.
     * If the argument was not provided and {@code optional} is set to {@code true}, returns null.*/
    private static String arg(String name, Map<String, String> arguments, boolean optional) {
        if (arguments.size() == 1 && arguments.containsKey(null)) return arguments.get(null);

        if (optional || arguments.containsKey(name)) {
            return arguments.get(name);
        } else {
            throw new RevConverter.RevConversionError("Missing argument " + name + ".");
        }
    }

    /**
     * Builds an Rev function call with the Rev function name and the arguments. The arguments are given as a list
     * [argumentName1, argumentValue1, argumentName2, argumentValue2, ...].
     */
    private static StringBuilder build(String revFunctionName, String... arguments) {
        StringBuilder builder = new StringBuilder();
        builder.append(revFunctionName).append("( ");

        for (int i = 0; i < arguments.length / 2; i++) {
            String argumentName = arguments[2*i];
            String argument = arguments[2*i + 1];

            if (argument == null) {
                // this is not provided, we skip it
                continue;
            }

            builder.append(argumentName).append("=").append(argument);
            builder.append(", ");
        }
        // remove trailing ", " if necessary
        if (builder.substring(builder.length() - 2).equals(", ")) {
            builder.setLength(builder.length() - 2);
        }

        builder.append(" )");
        return builder;
    }
}
