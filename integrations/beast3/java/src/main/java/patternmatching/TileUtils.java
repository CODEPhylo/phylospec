package patternmatching;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

public class TileUtils {
    public static Type getParametricType(Object object, int index) {
        // parse the type parameter T
        Type superclass = object.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[index];
        } else {
            return null;
        }
    }
}
