package org.phylospec.ast;

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
     * @param rules the rules that define how operand types are connected to the result type.
     * @param query the query that is matched against the rules.
     * @return the resulting type or null if no match is found.
     */
    Set<ResolvedType> findMatch(List<Rule> rules, Query query) {
        Set<ResolvedType> exactMatch = findExactMatch(rules, query);
        if (!exactMatch.isEmpty()) {
            return exactMatch;
        }

        // we couldn't find a direct match
        // let's try to go up the type hierarchy of the query to see if there is
        // a match for widened types
        while (true) {
            // TODO: make more flexible to support generics and be more elegant (also, the widening is wrong atm)
            for (int i = 0; i < query.inputTypes.length; i++) {
                Set<ResolvedType> widenedInputType = new HashSet<>();
                for (ResolvedType type : query.inputTypes[i]) {
                    if (type.getExtends() != null) {
                        // we replace this type with its direct parent
                        ResolvedType widenedType = ResolvedType.fromString(type.getExtends(), componentResolver);
                        if (widenedType != null) widenedInputType.add(widenedType);
                    }
                }

                if (widenedInputType.isEmpty()) return Set.of();

                Query widenedQuery = new Query(query.operation, widenedInputType);

                Set<ResolvedType> match = findExactMatch(rules, widenedQuery);
                if (!match.isEmpty()) return match;
            }
        }
    }

    /** Checks if there is a perfectly matching rule the given query and returns it.
     * Returns null if not match is found. */
    private Set<ResolvedType> findExactMatch(List<Rule> rules, Query query) {
        Set<ResolvedType> resultTypesOfMatches = new HashSet<>();

        for (Rule rule : rules) {
            if (rule.operation != query.operation) continue;
            if (!(componentResolver.canResolveType(rule.resultType))) continue;

            boolean matches = true;
            for (int i = 0; i < query.inputTypes.length; i++) {
                if (rule.inputTypes[i].equals(ANY)) continue;

                // we have to check if there is an overlap
                final int j = i;
                if (query.inputTypes[i].stream().noneMatch(x -> x.getName().equals(rule.inputTypes[j]))) {
                    matches =  false;
                    break;
                }
            }

            if (matches) {
                resultTypesOfMatches.add(ResolvedType.fromString(rule.resultType, componentResolver));
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
