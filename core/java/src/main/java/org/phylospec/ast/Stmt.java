package org.phylospec.ast;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Objects;

/**
 * Statements are the top-level nodes in the AST tree and correspond to executable
 * statements. This class has a number of subclasses for different types of expressions
 * like {@link Stmt.Assignment} or {@link Stmt.Draw}.
 */
public abstract class Stmt extends AstNode {

    abstract public <S, E, T> S accept(AstVisitor<S, E, T> visitor);

    abstract public String getName();

    /** Identifies which block a statement belongs to. */
    public sealed interface Block permits Block.NoBlock, Block.Data, Block.Model, Block.Mcmc, Block.Custom {
        record NoBlock() implements Block {}
        record Data() implements Block {
            @Override
            public String toString() {
                return "data";
            }
        }
        record Model() implements Block {
            @Override
            public String toString() {
                return "model";
            }
        }
        record Mcmc() implements Block {
            @Override
            public String toString() {
                return "mcmc";
            }
        }
        record Custom(String blockName) implements Block {
            @Override
            public String toString() {
                return blockName;
            }
        }

        Block NO_BLOCK = new NoBlock();
        Block DATA = new Data();
        Block MODEL = new Model();
        Block MCMC = new Mcmc();
    }

    public Block block = Block.NO_BLOCK;

    /** Represents an assignment like `Real value = 10`. */
    public static class Assignment extends Stmt {
        public Assignment(AstType type, String name, Expr expression) {
            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        @JsonPropertyDescription("The type of variable being assigned.")
        public AstType type;
        @JsonPropertyDescription("The variable name.")
        public final String name;
        @JsonPropertyDescription("The expression being assigned to the variable.")
        public Expr expression;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Assignment that = (Assignment) o;
            return Objects.equals(type, that.type) && Objects.equals(name, that.name) && Objects.equals(expression, that.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, expression);
        }

        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitAssignment(this);
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    /** Represents a draw like `Real value ~ Normal(mean=1, sd=1)`. */
    public static class Draw extends Stmt {
        public Draw(AstType type, String name, Expr expression) {
            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        @JsonPropertyDescription("The type of variable being drawn.")
        public AstType type;
        @JsonPropertyDescription("The variable name.")
        public final String name;
        @JsonPropertyDescription("The expression being drawn from.")
        public Expr expression;

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Draw that = (Draw) o;
            return Objects.equals(type, that.type) && Objects.equals(name, that.name) && Objects.equals(expression, that.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, expression);
        }

        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitDraw(this);
        }
    }

    /** Represents a decorated statement like `@observed() Real value ~ Normal(mean=1, sd=1)`.
     * The decorator itself is always a function call, whereas the decorated statement
     * can be any statement (even another decorated one).*/
    public static class Decorated extends Stmt {
        public Decorated(Expr.Call decorator, Stmt statement) {
            this.decorator = decorator;
            this.statement = statement;
        }

        @JsonPropertyDescription("The decorator call.")
        public final Expr.Call decorator;
        @JsonPropertyDescription("The decorated statement.")
        public Stmt statement;

        @Override
        public String getName() {
            return this.statement.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Decorated decorated = (Decorated) o;
            return Objects.equals(decorator, decorated.decorator) && Objects.equals(statement, decorated.statement);
        }

        @Override
        public int hashCode() {
            return Objects.hash(decorator, statement);
        }

        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitDecoratedStmt(this);
        }
    }

    /** Represents an indexed statement like `Real value[i] ~ Normal(mean=1, sd=1) for i in 1:10`. It consists of the
     * statement without index (`Real value[i] ~ Normal(mean=1, sd=1)`), the index (`ì`), and the range (`1:10`) */
    public static class Indexed extends Stmt {
        public Indexed(Stmt statement, List<Expr.Variable> indices, List<Expr> ranges) {
            this.statement = statement;
            this.indices = indices;
            this.ranges = ranges;
        }

        @JsonPropertyDescription("The indexed statement.")
        public Stmt statement;
        @JsonPropertyDescription("The index variables.")
        public final List<Expr.Variable> indices;
        @JsonPropertyDescription("The ranges over which the indices go.")
        public final List<Expr> ranges;

        @Override
        public String getName() {
            return this.statement.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Indexed indexed = (Indexed) o;
            return Objects.equals(statement, indexed.statement) && Objects.equals(indices, indexed.indices) && Objects.equals(ranges, indexed.ranges);
        }

        @Override
        public int hashCode() {
            return Objects.hash(statement, indices, ranges);
        }

        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitIndexedStmt(this);
        }
    }

    /** Represents an import statement like `use revbayes.core`. */
    public static class Import extends Stmt {
        public Import(List<String> namespace) {
            this.namespace = namespace;
        }

        @JsonPropertyDescription("The imported namespace name.")
        public final List<String> namespace;

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Import anImport = (Import) o;
            return Objects.equals(namespace, anImport.namespace);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(namespace);
        }

        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitImport(this);
        }
    }

    /** Represents an observed statement like `Real value ~ Normal(mean=1, sd=1) observed as 10.0`. */
    public static class ObservedAs extends Stmt {
        public ObservedAs(Stmt stmt, Expr observedAs) {
            this.stmt = stmt;
            this.observedAs = observedAs;
        }

        @JsonPropertyDescription("The statement observed.")
        public Stmt stmt;
        @JsonPropertyDescription("The observed value.")
        public Expr observedAs;

        @Override
        public String getName() {
            return this.stmt.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ObservedAs that = (ObservedAs) o;
            return Objects.equals(stmt, that.stmt) && Objects.equals(observedAs, that.observedAs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stmt, observedAs);
        }

        @Override
        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitObservedAsStmt(this);
        }
    }

    /** Represents an observed between statement like `Real value ~ Normal(mean=1, sd=1) observed between [10.0, 12.0]`. */
    public static class ObservedBetween extends Stmt {
        public ObservedBetween(Stmt stmt, Expr observedFrom, Expr observedTo) {
            this.stmt = stmt;
            this.observedFrom = observedFrom;
            this.observedTo = observedTo;
        }

        @JsonPropertyDescription("The statement observed.")
        public Stmt stmt;
        @JsonPropertyDescription("The lower bound.")
        public Expr observedFrom;
        @JsonPropertyDescription("The upper bound.")
        public Expr observedTo;

        @Override
        public String getName() {
            return this.stmt.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ObservedBetween that = (ObservedBetween) o;
            return Objects.equals(stmt, that.stmt) && Objects.equals(observedFrom, that.observedFrom) && Objects.equals(observedTo, that.observedTo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stmt, observedFrom, observedTo);
        }

        @Override
        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitObservedBetweenStmt(this);
        }
    }
}
