package tiling;

import java.util.Set;

/**
 * Result of one tile's attempt to match an AST node.
 *
 * Three outcomes are possible:
 *   - Irrelevant: the tile does not handle this node type or generator name at all.
 *                 These are silently skipped and excluded from diagnostic output.
 *   - Rejected:   the tile was relevant (right type/name) but failed a later check.
 *                 The reason string is collected and shown when no tile wins.
 *   - Matched:    the tile matched and produced one or more wired instances.
 */
public sealed interface TilingAttempt permits TilingAttempt.Irrelevant, TilingAttempt.Rejected, TilingAttempt.Matched {

    record Irrelevant() implements TilingAttempt {}
    record Rejected(String reason) implements TilingAttempt {}
    record Matched(Set<Tile<?>> tiles) implements TilingAttempt {}

    static TilingAttempt irrelevant() { return new Irrelevant(); }
    static TilingAttempt rejected(String reason) { return new Rejected(reason); }
    static TilingAttempt matched(Set<Tile<?>> tiles) { return new Matched(tiles); }
}
