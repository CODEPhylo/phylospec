package tiling;

import org.phylospec.ast.AstNode;

public abstract class FailedTilingAttempt extends Throwable {

    public static class Irrelevant extends FailedTilingAttempt {
    }

    public static class Rejected extends FailedTilingAttempt {
        private final String reason;

        public Rejected(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

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
