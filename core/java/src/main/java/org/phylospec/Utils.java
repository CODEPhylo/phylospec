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
    ///
    /// If the order of the visitor calls is important, consider using {@code visitOrderedCombinations}.
    public static <T> void visitCombinations(List<Set<T>> variants, Consumer<List<T>> visitor) {
        boolean fullyResolved = true;

        for (int i = 0; i < variants.size(); i++) {
            Set<T> parameterTypeSet = variants.get(i);

            if (parameterTypeSet.size() == 1) continue;

            for (T parameterType : parameterTypeSet) {
                Set<T> clonedParameterTypeSet = new HashSet<>();
                clonedParameterTypeSet.add(parameterType);

                List<Set<T>> clonedTypeParams = new ArrayList<>(variants);
                clonedTypeParams.set(i, clonedParameterTypeSet);

                visitCombinations(clonedTypeParams, visitor);
            }

            return;
        }

        visitor.accept(
                variants.stream().map(x -> x.iterator().next()).collect(Collectors.toList())
        );
    }

    /// Calls the given visitor function for every combination of the given variants.
    ///
    /// A combination is a list of the same size as {@code variants}.
    /// The i-th element of a combination is one of the items in the i-th {@code Set<T>}
    /// in {@code variants}.
    ///
    /// Compared to {@code visitCombinations}, here the order of the visitor calls matches the given order of the
    /// variants.
    public static <T> void visitOrderedCombinations(List<List<T>> variants, Consumer<List<T>> visitor) {
        boolean fullyResolved = true;

        for (int i = 0; i < variants.size(); i++) {
            List<T> parameterTypeSet = variants.get(i);

            if (parameterTypeSet.size() == 1) continue;

            for (T parameterType : parameterTypeSet) {
                List<T> clonedParameterTypeSet = new ArrayList<>();
                clonedParameterTypeSet.add(parameterType);

                List<List<T>> clonedTypeParams = new ArrayList<>(variants);
                clonedTypeParams.set(i, clonedParameterTypeSet);

                visitOrderedCombinations(clonedTypeParams, visitor);
            }

            return;
        }

        visitor.accept(
                variants.stream().map(x -> x.iterator().next()).collect(Collectors.toList())
        );
    }

    public static int editDistance(String a, String b) {
        if (a.equalsIgnoreCase(b)) return 0;
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
                }
            }
            prev = curr;
        }
        return prev[b.length()];
    }
}
