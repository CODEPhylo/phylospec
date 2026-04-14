package tiling;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class TypeToken<T> {
    private final Type type;

    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }

    private TypeToken(Type type) {
        this.type = type;
    }

    /** Wraps an existing {@link Type} in a {@code TypeToken}. */
    public static TypeToken<?> of(Type type) {
        return new TypeToken<>(type) {};
    }

    /** Constructs a parameterized type at runtime, e.g. {@code parameterized(List.class, String.class)}. */
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

    public Type getType() {
        return type;
    }

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
