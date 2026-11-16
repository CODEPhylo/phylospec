package org.phylospec.tiling.tiles;

import java.util.List;
import org.phylospec.components.Generator__1;

/**
 * This class represents template tiles that only recognize models the engine cannot handle, in order to reject
 * them with an error. Their templates are therefore not generator patterns the engine supports, and they are
 * not exposed in the engine specification. They have the error priority, so they override any other tiling.
 */
public abstract class ErrorTile<T, S> extends TemplateTile<T, S> {

    @Override
    public List<Generator__1> getGeneratorPatterns() {
        return List.of();
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.ERROR;
    }
}
