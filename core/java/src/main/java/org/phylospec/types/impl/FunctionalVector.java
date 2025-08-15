package org.phylospec.types.impl;

import org.phylospec.types.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Default implementation of Vector with functional operations.
 */
public class FunctionalVector<T extends PhyloSpecType> implements Vector<T> {
    private final List<T> elements;
    
    public FunctionalVector(List<T> elements) {
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }
    
    @Override
    public List<T> getElements() {
        return elements;
    }
    
    @Override
    public int size() {
        return elements.size();
    }
    
    @Override
    public T get(int index) {
        return elements.get(index);
    }
    
    // ===== FUNCTIONAL OPERATIONS =====
    
    public FunctionalVector<T> filter(Predicate<T> predicate) {
        List<T> filtered = new ArrayList<>();
        for (T element : elements) {
            if (predicate.test(element)) {
                filtered.add(element);
            }
        }
        return new FunctionalVector<>(filtered);
    }
    
    public <U extends PhyloSpecType> FunctionalVector<U> map(Function<T, U> f) {
        List<U> mapped = new ArrayList<>(size());
        for (T element : elements) {
            mapped.add(f.apply(element));
        }
        return new FunctionalVector<>(mapped);
    }
    
    public FunctionalVector<T> take(int n) {
        if (n <= 0) return empty();
        if (n >= size()) return this;
        return new FunctionalVector<>(elements.subList(0, n));
    }
    
    public FunctionalVector<T> append(T element) {
        List<T> newList = new ArrayList<>(elements);
        newList.add(element);
        return new FunctionalVector<>(newList);
    }
    
    // ===== STATIC FACTORIES =====
    
    public static <T extends PhyloSpecType> FunctionalVector<T> empty() {
        return new FunctionalVector<>(Collections.emptyList());
    }
    
    public static <T extends PhyloSpecType> FunctionalVector<T> of(T... elements) {
        return new FunctionalVector<>(Arrays.asList(elements));
    }
    
    public static <T extends PhyloSpecType> FunctionalVector<T> ofAll(Collection<T> elements) {
        return new FunctionalVector<>(new ArrayList<>(elements));
    }
}