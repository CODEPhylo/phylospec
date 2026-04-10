package tiling;

import org.phylospec.ast.AstNode;

/**
 * This error wraps information on a failed attempt at applying a tile to an AST subgraph.
 */
public abstract class FailedTilingAttempt extends Throwable {

    /** This error should be raised when the tile is completely irrelevant for an AST subgraph. */
    public static class Irrelevant extends FailedTilingAttempt {
    }

    /** This error indicates that the tile is relevant but failed due to miscellaneous reasons. */
    public static class Rejected extends FailedTilingAttempt {
        private final String reason;

        public Rejected(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    /** This error indicates that the tile is relevant but failed due to an incompatibility at the tile boundary. */
    public static class RejectedBoundary extends FailedTilingAttempt {
        private final String reason;
        private final AstNode otherNode;

        public RejectedBoundary(String reason, AstNode otherNode) {
            this.reason = reason;
            this.otherNode = otherNode;
        }

        public String getReason() {
            return reason;
        }

        public AstNode getOtherNode() {
            return otherNode;
        }
    }

    /** This error indicates that the tile is relevant but failed due a missing tile at the interface. */
    public static class RejectedCascade extends FailedTilingAttempt {
        private final AstNode otherNode;

        public RejectedCascade(AstNode otherNode) {
            this.otherNode = otherNode;
        }

        public AstNode getOtherNode() {
            return otherNode;
        }
    }

}
