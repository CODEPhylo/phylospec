package org.phylospec.ast;

import java.util.Objects;

/**
 * Statements are the top-level nodes in the AST tree and correspond to executable
 * statements. This class has a number of subclasses for different types of expressions
 * like {@link Stmt.Assignment} or {@link Stmt.Draw}.
 */
public abstract class Stmt {

    abstract public <S, E, T> S accept(AstVisitor<S, E, T> visitor);

    /** Represents an assignment like `Real value = 10`. */
    public static class Assignment extends Stmt {
        public Assignment(Type type, String name, Expr expression) {
            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        final Type type;
        final String name;
        final Expr expression;

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
    }

    /** Represents a draw like `Real value ~ Normal(mean=1, sd=1)`. */
    public static class Draw extends Stmt {
        public Draw(Type type, String name, Expr expression) {
            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        final Type type;
        final String name;
        final Expr expression;

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
        public Decorated(Expr.Call decorator, Stmt statememt) {
            this.decorator = decorator;
            this.statememt = statememt;
        }

        final Expr.Call decorator;
        final Stmt statememt;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Decorated decorated = (Decorated) o;
            return Objects.equals(decorator, decorated.decorator) && Objects.equals(statememt, decorated.statememt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(decorator, statememt);
        }

        public <S, E, T> S accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitDecoratedStmt(this);
        }
    }

}
