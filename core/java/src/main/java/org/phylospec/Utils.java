package org.phylospec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Utils {
    public static <T> void visitCombinations(Consumer<List<T>> visitor, List<Set<T>> combinations) {
        boolean fullyResolved = true;

        for (int i = 0; i < combinations.size(); i++) {
            Set<T> parameterTypeSet = combinations.get(i);

            if (parameterTypeSet.size() == 1) continue;

            for (T parameterType : parameterTypeSet) {
                Set<T> clonedParameterTypeSet = new HashSet<>();
                clonedParameterTypeSet.add(parameterType);

                List<Set<T>> clonedTypeParams = new ArrayList<>(combinations);
                clonedTypeParams.set(i, clonedParameterTypeSet);

                visitCombinations(visitor, clonedTypeParams);
            }

            fullyResolved = false;
        }

        if (fullyResolved) visitor.accept(
                combinations.stream().map(x -> x.iterator().next()).collect(Collectors.toList())
        );
    }
}
