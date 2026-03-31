package patternmatching;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class TileUtils {
    public static Type getParametricType(Object object) {
        // parse the type parameter T
        Type superclass = object.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[0];
        } else {
            return null;
        }
    }
}
