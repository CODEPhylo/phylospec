package org.phylospec.converters;

import java.util.Map;

public class RevGeneratorMapping {
    /**
     * Takes a PhyloSpec generator name and an arguments map and returns the corresponding Rev expression. Returns
     * null if the generator cannot be mapped to Rev Rev code.
     */
    static StringBuilder map(String phylospecGenerator, Map<String, String> arguments) {
        return switch (phylospecGenerator) {
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
            case "Yule" -> build(
                    "Yule",
                    "lambda", arg("birthRate", arguments),
                    "taxa", arg("taxa", arguments, true)
            );
            case "FossilBirthDeath" -> build(
                    "FossilBirthDeathTree",
                    "lambda", arg("birthRate", arguments),
                    "mu", arg("deathRate", arguments),
                    "rho", arg("rho", arguments),
                    "psi", arg("samplingRate", arguments),
                    "taxa", arg("taxa", arguments, true)
            );
            case "BirthDeath" -> build(
                    "BirthDeath",
                    "lambda", arg("birthRate", arguments),
                    "mu", arg("deathRate", arguments),
                    "rootAge", arg("rootHeight", arguments),
                    "taxa", arg("taxa", arguments, true)
            );
            case "Coalescent" -> build(
                    "Coalescent",
                    "theta", arg("populationSize", arguments),
                    "taxa", arg("taxa", arguments, true)
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
                    "PhyloBrownian",
                    "tree", arg("tree", arguments),
                    "diffRate", arg("sigma", arguments),
                    "y0", arg("rootValue", arguments)
            );
            case "PhyloOU" -> build(
                    "PhyloOU",
                    "tree", arg("tree", arguments),
                    "diffRate", arg("sigma", arguments),
                    "theta", arg("optimum", arguments),
                    "alpha", arg("alpha", arguments),
                    "y0", arg("rootValue", arguments)
            );
            case "PhyloCTMC" -> build(
                    "PhyloCTMC",
                    "tree", arg("tree", arguments),
                    "Q", arg("Q", arguments),
                    "siteRates", arg("siteRates", arguments, true),
                    "branchRates", arg("branchRates", arguments, true),
                    "L", arg("numSequences", arguments, true)
            );
            case "IID" -> {
                // we have smth like IID(base=Normal(...), n=5)
                // we turn this into Normal(..., replicates=5)
                String distribution = arg("base", arguments);
                String replicates = arg("n", arguments);

                if (!distribution.endsWith(")")) {
                    throw new RevConverter.RevConversionError("IID is only supported when the base distribution is directly passed to the function.");
                }

                StringBuilder builder = new StringBuilder();
                builder.append(distribution.substring(0, distribution.length() - 1));
                builder.append(", replicates=").append(replicates).append(")");

                yield builder;
            }
            default -> throw new RevConverter.RevConversionError("Generator " + phylospecGenerator + " is not supported.");
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
            throw new RevConverter.RevConversionError("Missing argument " + name + ". This should be caught by the type resolver.");
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
