package org.phylospec.converters;

import java.util.Map;

public class LPhyGeneratorMapping {
    static StringBuilder map(String phylospecGenerator, Map<String, String> arguments) {
        return switch (phylospecGenerator) {
            case "Exponential" -> build(
                    "Exp",
                    "mean", "1/(" + arg("rate", arguments) + ")"
            );
            case "Normal" -> build(
                    "Normal",
                    "mean", arg("mean", arguments),
                    "sd", arg("sd", arguments)
            );
            case "LogNormal" -> build(
                    "LogNormal",
                    "meanlog", arg("meanlog", arguments),
                    "sdlog", arg("sdlog", arguments)
            );
            case "Gamma" -> build(
                    "Gamma",
                    "shape", arg("shape", arguments),
                    "scale", "1/(" + arg("rate", arguments) + ")"
            );
            case "Beta" -> build(
                    "Beta",
                    "alpha", arg("alpha", arguments),
                    "beta", arg("beta", arguments)
            );
            case "Uniform" -> build(
                    "Uniform",
                    "lower", arg("lower", arguments),
                    "upper", arg("upper", arguments)
            );
            case "Dirichlet" -> build(
                    "Dirichlet",
                    "conc", arg("alpha", arguments)
            );
            case "Yule" -> build(
                    "Yule",
                    "lambda", arg("birthRate", arguments)
            );
            case "FossilBirthDeath" -> build(
                    "FossilBirthDeathTree",
                    "lambda", arg("birthRate", arguments),
                    "mu", arg("deathRate", arguments),
                    "rho", arg("samplingRate", arguments)
            );
            case "BirthDeath" -> build(
                    "BirthDeath",
                    "lambda", arg("birthRate", arguments),
                    "mu", arg("deathRate", arguments),
                    "rootAge", arg("rootHeight", arguments)
            );
            case "Coalescent" -> build(
                    "Coalescent",
                    "theta", arg("populationSize", arguments)
            );
            case "JC69" -> build(
                    "jukesCantor"
            );
            case "K80" -> build(
                    "k80",
                    "kappa",  arg("kappa", arguments)
            );
            case "F81" -> build(
                    "f81",
                    "freq",  arg("baseFrequencies", arguments)
            );
            case "HKY" -> build(
                    "hky",
                    "kappa",  arg("kappa", arguments),
                    "freq",  arg("baseFrequencies", arguments)
            );
            case "GTR" -> build(
                    "gtr",
                    "rates",  arg("rateMatrix", arguments),
                    "freq",  arg("baseFrequencies", arguments)
            );
            case "DiscreteGamma" -> build(
                    "DiscretizeGamma",
                    "shape",  arg("shape", arguments),
                    "ncat",  arg("categories", arguments)
            );
            case "nexus" -> build(
                    "readNexus",
                    "file",  arg("file", arguments)
            );
            case "fasta" -> build(
                    "readFasta",
                    "file",  arg("file", arguments)
            );
            default -> throw new LPhyConverter.LPhyConversionError("Generator " + phylospecGenerator + " is not supported.");
        };
    }

    private static String arg(String name, Map<String, String> arguments) {
        if (arguments.size() == 1 && arguments.containsKey(null)) return arguments.get(null);

        if (arguments.containsKey(name)) {
            return arguments.get(name);
        } else {
            throw new LPhyConverter.LPhyConversionError("Missing argument " + name + ". This should be caught by the type resolver.");
        }
    }

    private static StringBuilder build(String generatorName, String... arguments) {
        StringBuilder builder = new StringBuilder();
        builder.append(generatorName).append("(");

        for (int i = 0; i < arguments.length / 2; i++) {
            String argumentName = arguments[2*i];
            String argument = arguments[2*i + 1];
            builder.append(argumentName).append("=").append(argument);

            if (i < arguments.length / 2 - 1) {
                builder.append(", ");
            }
        }

        builder.append(")");
        return builder;
    }
}
