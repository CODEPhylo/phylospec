package org.phylospec.typeresolver;

import java.util.*;
import org.phylospec.typeresolver.properties.TypePropertyEngine;

/**
 * A {@link Set} of {@link ResolvedType} that dedupes by structural equality (see
 * {@link ResolvedType#equals}), which intentionally ignores attached type properties.
 *
 * A plain {@code HashSet<ResolvedType>} would silently keep whichever structurally-equal
 * instance was inserted first and discard the properties of every later one (e.g. two
 * literal {@code Integer} values with different {@code VALUE} properties). This class
 * instead reconciles properties on collision: when a structurally-equal type is added, the
 * two candidates are merged into a fresh instance that only keeps the properties both agree
 * on, recursively into type parameters too, using the same rule as
 * {@link TypePropertyEngine#getPropertiesInAgreement}. Neither original instance is mutated.
 */
public class ResolvedTypeSet extends AbstractSet<ResolvedType> {

    private final Map<ResolvedType, ResolvedType> elements;

    public ResolvedTypeSet() {
        this.elements = new HashMap<>();
    }

    public ResolvedTypeSet(Collection<? extends ResolvedType> initial) {
        this();
        addAll(initial);
    }

    public static ResolvedTypeSet copyOf(Collection<? extends ResolvedType> types) {
        return new ResolvedTypeSet(types);
    }

    public static ResolvedTypeSet empty() {
        return new ResolvedTypeSet();
    }

    public static ResolvedTypeSet of(ResolvedType... types) {
        return new ResolvedTypeSet(Arrays.stream(types).toList());
    }

    @Override
    public boolean add(ResolvedType candidate) {
        if (elements.containsKey(candidate)) {
            // a structurally-equal type is already present: reconcile the properties instead of
            // silently dropping the candidate's ones

            ResolvedType existing = elements.get(candidate);
            elements.put(existing, merge(existing, candidate));

            return false;
        } else {
            // we don't know this type yet
            elements.put(candidate, candidate);

            return true;
        }
    }

    @Override
    public boolean contains(Object o) {
        return elements.containsKey(o);
    }

    @Override
    public Iterator<ResolvedType> iterator() {
        return elements.values().iterator();
    }

    @Override
    public int size() {
        return elements.size();
    }

    /**
     * Merges two structurally-equal resolved types into a fresh instance that keeps only the
     * properties both agree on, recursively for every type parameter. Neither input is mutated,
     * so instances shared elsewhere (e.g. the arguments of {@link TypeUtils#getLowestCover}) stay
     * untouched.
     */
    public static ResolvedType merge(ResolvedType existing, ResolvedType candidate) {
        if (!existing.equals(candidate)) {
            throw new RuntimeException("Trying to merge two incompatible types. This should not happen.");
        }

        Map<String, ResolvedType> mergedParameterTypes = new HashMap<>();
        for (String parameterName : existing.getParametersNames()) {
            ResolvedType existingParameter = existing.getParameterTypes().get(parameterName);
            ResolvedType candidateParameter = candidate.getParameterTypes().get(parameterName);
            if (existingParameter != null && candidateParameter != null) {
                mergedParameterTypes.put(parameterName, merge(existingParameter, candidateParameter));
            }
        }

        ResolvedType merged = existing.withParameterTypes(mergedParameterTypes);
        merged.properties().replace(TypePropertyEngine.getPropertiesInAgreement(existing, candidate));

        return merged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolvedTypeSet other)) return false;
        if (size() != other.size()) return false;

        // structural equality already guarantees a matching element exists; we additionally
        // require that its properties agree

        for (ResolvedType element : elements.values()) {
            ResolvedType otherElement = other.elements.get(element);
            if (otherElement == null || !element.equalsIncludingProperties(otherElement)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        // matches the contract of Set#hashCode (order-independent sum), but based on
        // hashCodeIncludingProperties so that property differences affect the hash too

        int hash = 0;
        for (ResolvedType element : elements.values()) {
            hash += element.hashCodeIncludingProperties();
        }
        return hash;
    }

    public ResolvedType get(ResolvedType resolvedType) {
        return elements.get(resolvedType);
    }
}
