package tiling;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Captures a full generic type at runtime, working around Java's type erasure.
///
/// Java erases generic type parameters at runtime, so {@code List<String>.class} does not
/// exist and cannot be obtained via normal reflection. The standard workaround is to create an
/// anonymous subclass whose generic superclass is baked into the bytecode and can be recovered
/// via {@link Class#getGenericSuperclass()}. This class automates that pattern:
///
/// ```java
/// // captures the full type List<String> at runtime
/// TypeToken<List<String>> token = new TypeToken<List<String>>() {};
/// ```
///
/// Beyond type capture, this class provides {@link #isAssignableFrom} — a generics-aware
/// assignability check that {@link Class#isAssignableFrom} cannot perform because it only
/// operates on raw types.
public abstract class TypeToken<T> {
    private final Type type;

    /**
     * Captures the generic type argument {@code T} from the anonymous subclass created by the
     * caller. Must be invoked as {@code new TypeToken<Foo>() {}} — direct instantiation would
     * lose the type argument.
     */
    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }

    private TypeToken(Type type) {
        this.type = type;
    }

    /**
     * Wraps an existing {@link Type} (obtained from reflection) in a {@code TypeToken}.
     * Use this when you already have a {@link Type} object rather than writing it as a literal.
     */
    public static TypeToken<?> of(Type type) {
        return new TypeToken<>(type) {};
    }

    /**
     * Builds a parameterized type token at runtime, e.g. {@code parameterized(List.class, String.class)}.
     * Needed when the type arguments are only known at runtime and cannot be written as a literal.
     */
    public static TypeToken<?> parameterized(Class<?> raw, Type... typeArgs) {
        ParameterizedType pt = new ParameterizedType() {
            @Override public Type[] getActualTypeArguments() { return typeArgs.clone(); }
            @Override public Type getRawType() { return raw; }
            @Override public Type getOwnerType() { return null; }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof ParameterizedType other)) return false;
                return raw.equals(other.getRawType())
                        && Arrays.equals(typeArgs, other.getActualTypeArguments())
                        && Objects.equals(null, other.getOwnerType());
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(typeArgs) ^ raw.hashCode();
            }
        };
        return new TypeToken<>(pt) {};
    }

    /**
     * Returns the first type argument of {@code container} as a {@code TypeToken}, or {@code null}
     * if the type is not parameterized or the argument is still an unresolved variable or wildcard.
     * Use this to "unwrap" a generic container, e.g. extracting {@code T} from {@code Foo<T>}.
     */
    public static TypeToken<?> firstConcreteTypeArg(TypeToken<?> container) {
        if (container.getType() instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (!(arg instanceof TypeVariable) && !(arg instanceof WildcardType)) {
                return TypeToken.of(arg);
            }
        }
        return null;
    }

    /**
     * Builds a {@code TypeToken<List<E>>} from an element token.
     */
    public static TypeToken<?> listOf(TypeToken<?> element) {
        return TypeToken.parameterized(List.class, element.getType());
    }

    /** Returns the underlying {@link Type} represented by this token. */
    public Type getType() {
        return type;
    }

    /**
     * Returns {@code true} if this type is assignable from {@code other}, including
     * full generic type argument checking that {@link Class#isAssignableFrom} cannot perform.
     * For example, {@code TypeToken<List<String>>} is <em>not</em> assignable from
     * {@code TypeToken<List<Integer>>}, even though raw {@code List} would be.
     */
    public boolean isAssignableFrom(TypeToken<?> other) {
        return isAssignable(type, other.type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeToken<?> other)) return false;
        return type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    /**
     * Core recursive assignability check that handles raw classes, parameterized types,
     * and wildcards. For parameterized types it walks the source's supertype chain to find
     * a compatible instantiation of the target's raw type, then checks each type argument.
     */
    private static boolean isAssignable(Type target, Type source) {
        if (target.equals(source)) return true;

        if (target instanceof WildcardType wildcard) {
            for (Type upper : wildcard.getUpperBounds()) {
                if (!isAssignable(upper, source)) return false;
            }
            for (Type lower : wildcard.getLowerBounds()) {
                if (!isAssignable(source, lower)) return false;
            }
            return true;
        }

        if (target instanceof Class<?> targetClass) {
            if (source instanceof Class<?> sourceClass)
                return targetClass.isAssignableFrom(sourceClass);
            if (source instanceof ParameterizedType pt)
                return targetClass.isAssignableFrom((Class<?>) pt.getRawType());
        }

        if (target instanceof ParameterizedType targetPt) {
            // walk the source's generic supertype chain to find a parameterized version of targetRaw,
            // substituting any type variables along the way
            Type resolvedSource = resolveAsParameterized(source, (Class<?>) targetPt.getRawType(), Map.of());
            if (!(resolvedSource instanceof ParameterizedType sourcePt)) return false;

            Type[] targetArgs = targetPt.getActualTypeArguments();
            Type[] sourceArgs = sourcePt.getActualTypeArguments();
            if (targetArgs.length != sourceArgs.length) return false;

            for (int i = 0; i < targetArgs.length; i++) {
                if (!typeArgMatches(targetArgs[i], sourceArgs[i])) return false;
            }
            return true;
        }

        return false;
    }

    /**
     * Walks the generic supertype chain of {@code source} to find a parameterized
     * instantiation of {@code targetRaw}, substituting type variables as it descends.
     * Returns {@code null} if no match is found.
     */
    private static Type resolveAsParameterized(Type source, Class<?> targetRaw, Map<TypeVariable<?>, Type> subs) {
        if (source instanceof ParameterizedType pt) {
            Class<?> rawClass = (Class<?>) pt.getRawType();
            if (targetRaw.equals(rawClass)) return substituteType(pt, subs);

            // build substitution map: each type parameter of rawClass → the (substituted) type arg
            TypeVariable<?>[] params = rawClass.getTypeParameters();
            Type[] args = pt.getActualTypeArguments();
            Map<TypeVariable<?>, Type> newSubs = new HashMap<>(subs);
            for (int i = 0; i < params.length; i++)
                newSubs.put(params[i], substituteType(args[i], subs));

            return resolveAsParameterized(rawClass, targetRaw, newSubs);
        }

        if (!(source instanceof Class<?> sourceClass)) return null;
        if (!targetRaw.isAssignableFrom(sourceClass)) return null;

        // check generic superclass
        Type genericSuper = sourceClass.getGenericSuperclass();
        if (genericSuper != null) {
            Type result = resolveAsParameterized(genericSuper, targetRaw, subs);
            if (result != null) return result;
        }

        // check generic interfaces
        for (Type iface : sourceClass.getGenericInterfaces()) {
            Type result = resolveAsParameterized(iface, targetRaw, subs);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * Applies the given type-variable substitution map to {@code type}, replacing any
     * {@link TypeVariable} occurrences and rebuilding nested {@link ParameterizedType}s as needed.
     */
    private static Type substituteType(Type type, Map<TypeVariable<?>, Type> subs) {
        if (subs.isEmpty()) return type;
        if (type instanceof TypeVariable<?> tv)
            return subs.getOrDefault(tv, tv);
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            Type[] newArgs = new Type[args.length];
            boolean changed = false;
            for (int i = 0; i < args.length; i++) {
                newArgs[i] = substituteType(args[i], subs);
                if (!newArgs[i].equals(args[i])) changed = true;
            }
            return changed ? parameterized((Class<?>) pt.getRawType(), newArgs).getType() : pt;
        }
        return type;
    }

    // type arguments are invariant by default, but wildcards relax this:
    //   ? (unbounded)     → accepts any source type
    //   ? extends Foo     → source must be assignable to Foo
    //   ? super Foo       → Foo must be assignable to source
    private static boolean typeArgMatches(Type target, Type source) {
        if (target instanceof WildcardType wildcard) {
            for (Type upper : wildcard.getUpperBounds()) {
                if (!isAssignable(upper, source)) return false;
            }
            for (Type lower : wildcard.getLowerBounds()) {
                if (!isAssignable(source, lower)) return false;
            }
            return true;
        }
        return target.equals(source);
    }
}
