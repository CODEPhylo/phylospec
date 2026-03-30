package patternmatching;

import java.lang.reflect.Type;

public record EvaluatedTile(Tile tile, Object generatedObject, Type generatedType, int score) {
}

// add more info to generatedObject
// maybe make generatedObject generic to make BEAST a special case?
