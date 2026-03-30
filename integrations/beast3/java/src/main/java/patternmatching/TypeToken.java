package patternmatching;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeToken<T> {
    private final Type type;

    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
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
                if (!isAssignable(targetArgs[i], sourceArgs[i])) return false;
            }
            return true;
        }

        return false;
    }
}
