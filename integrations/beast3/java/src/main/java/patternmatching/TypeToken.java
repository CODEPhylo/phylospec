package patternmatching;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
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

    public boolean isAssignableFrom(Type other) {
        return isAssignable(type, other);
    }

    private static boolean isAssignable(Type target, Type source) {
        if (target.equals(source)) return true;

        if (target instanceof Class<?> targetClass) {
            if (source instanceof Class<?> sourceClass)
                return targetClass.isAssignableFrom(sourceClass);
            if (source instanceof ParameterizedType pt)
                return targetClass.isAssignableFrom((Class<?>) pt.getRawType());
        }

        if (target instanceof ParameterizedType targetPt) {
            if (!(source instanceof ParameterizedType sourcePt)) return false;

            if (!isAssignable(targetPt.getRawType(), sourcePt.getRawType())) return false;

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
