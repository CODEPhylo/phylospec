package org.phylospec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Utils {

    /// Calls the given visitor function for every combination of the given variants.
    ///
    /// A combination is a list of the same size as {@code variants}.
    /// The i-th element of a combination is one of the items in the i-th {@code Set<T>}
    /// in {@code variants}.
    public static <T> void visitCombinations(Consumer<List<T>> visitor, List<Set<T>> variants) {
        boolean fullyResolved = true;

        for (int i = 0; i < variants.size(); i++) {
            Set<T> parameterTypeSet = variants.get(i);

            if (parameterTypeSet.size() == 1) continue;

            for (T parameterType : parameterTypeSet) {
                Set<T> clonedParameterTypeSet = new HashSet<>();
                clonedParameterTypeSet.add(parameterType);

                List<Set<T>> clonedTypeParams = new ArrayList<>(variants);
                clonedTypeParams.set(i, clonedParameterTypeSet);

                visitCombinations(visitor, clonedTypeParams);
            }

            fullyResolved = false;
        }

        if (fullyResolved) visitor.accept(
                variants.stream().map(x -> x.iterator().next()).collect(Collectors.toList())
        );
    }
}
