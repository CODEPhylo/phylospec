package org.phylospec.typeresolver;

import org.phylospec.Utils;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.TokenType;

import java.util.*;

/**
 * This class helps to determine the type of the result of an operation on one or more operands.
 */
class TypeMatcher {
    private final ComponentResolver componentResolver;

    TypeMatcher(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

    static final String ANY = "ANY";

    /**
     * Determines the result type of an operation on one or more operands. Input types are widened
     * automatically if no exact match is found. Note that this currently does not work with generic
     * types.
     *
     * @param rules the rules that define how operand types are connected to the result type.
     * @param query the query that is matched against the rules.
     * @return the resulting type or null if no match is found.
     */
    Set<ResolvedType> findMatch(List<Rule> rules, Query query) {
        // we first enumerate all possible input type combinations
        Set<List<ResolvedType>> inputCombinations = new HashSet<>();
        Utils.visitCombinations(
                Arrays.stream(query.inputTypes).toList(),
                inputCombinations::add
        );

        // we now find all rules where the rule input types cover the actual input types
        Set<ResolvedType> resultTypesOfMatches = new HashSet<>();
        for (List<ResolvedType> inputCombination : inputCombinations) {
            for (Rule rule : rules) {
                if (rule.operation != query.operation) continue;
                if (rule.inputTypes.length != inputCombination.size()) continue;

                boolean allInputsMatch = true;
                for (int i = 0; i < inputCombination.size(); i++) {
                    if (!TypeUtils.covers(
                            ResolvedType.fromString(rule.inputTypes[i], componentResolver),
                            inputCombination.get(i),
                            componentResolver
                    )) {
                        allInputsMatch = false;
                        break;
                    }
                }

                if (allInputsMatch) resultTypesOfMatches.add(
                        ResolvedType.fromString(rule.resultType, componentResolver)
                );
            }
        }

        return resultTypesOfMatches;
    }

    static class Rule {
        public Rule(TokenType operation, String... types) {
            this.operation = operation;
            this.inputTypes = Arrays.stream(types).limit(types.length - 1).toArray(String[]::new);
            this.resultType = types[types.length - 1];
        }

        TokenType operation;
        String[] inputTypes;
        String resultType;
    }

    static class Query {
        public Query(TokenType operation, Set<ResolvedType>... inputTypes) {
            this.operation = operation;
            this.inputTypes = inputTypes;
        }

        TokenType operation;
        Set<ResolvedType>[] inputTypes;
    }

}
