package org.phylospec.ast;

import org.phylospec.lexer.TokenRange;

import java.util.List;
import java.util.Objects;

/**
 * Statements are the top-level nodes in the AST tree and correspond to executable
 * statements. This class has a number of subclasses for different types of expressions
 * like {@link Stmt.Assignment} or {@link Stmt.Draw}.
 */
public abstract class Stmt {

    abstract public <S, E, T> S accept(AstVisitor<S, E, T> visitor);

    public TokenRange tokenRange = null;

    /** Represents an assignment like `Real value = 10`. */
    public static class Assignment extends Stmt {
        public Assignment(AstType type, String name, Expr expression, TokenRange tokenRange) {
            this.type = type;
            this.name = name;
            this.expression = expression;
            this.tokenRange = tokenRange;
        }

        public final AstType type;
        public final String name;
        public final Expr expression;

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
        public Draw(AstType type, String name, Expr expression, TokenRange tokenRange) {
            this.type = type;
            this.name = name;
            this.expression = expression;
            this.tokenRange = tokenRange;
        }

        public final AstType type;
        public final String name;
        public final Expr expression;

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
            this.statememt = statement;
        }
        public Decorated(Expr.Call decorator, Stmt statement, TokenRange tokenRange) {
            this.decorator = decorator;
            this.statememt = statement;
            this.tokenRange = tokenRange;
        }

        public final Expr.Call decorator;
        public final Stmt statememt;

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

    /** Represents an import statement like `import revbayes.core`. */
    public static class Import extends Stmt {
        public Import(List<String> namespace) {
            this.namespace = namespace;
        }

        public final List<String> namespace;

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

}
