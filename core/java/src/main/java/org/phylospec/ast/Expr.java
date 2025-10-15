package org.phylospec.ast;

import org.phylospec.lexer.Token;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Expressions are a type of node in the AST tree. This class has a number of
 * subclasses for different types of expressions like {@link Expr.Variable} or
 * {@link Expr.Call}.
 * Expressions are always part of a {@link Stmt}.
 */
public abstract class Expr {

    abstract public <S, E, T> E accept(AstVisitor<S, E, T> visitor);

    /** Represents a variable. Function and distribution names are also treated
     * as variables. */
    public static class Variable extends Expr {
        public Variable(String variable) {
            this.variable = variable;
        }

        public final String variable;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Variable variable1 = (Variable) o;
            return Objects.equals(variable, variable1.variable);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(variable);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitVariable(this);
        }
    }

    /** Represents a literal, i.e. either a String, an Integer, or a
     * Real. */
	public static class Literal extends Expr {
		public Literal(Object value) {
			this.value = value;
		}

        // TODO: make this type generic
        public final Object value;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Literal literal = (Literal) o;
            return Objects.equals(value, literal.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitLiteral(this);
        }
    }

    /** Represents a unary operation, e.g. a negation. */
	public static class Unary extends Expr {
		public Unary(Token operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}

		public final Token operator;
        public final Expr right;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Unary unary = (Unary) o;
            return Objects.equals(operator, unary.operator) && Objects.equals(right, unary.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operator, right);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitUnary(this);
        }
    }

    /** Represents a binary operation, e.g. addition. */
	public static class Binary extends Expr {
		public Binary(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		public final Expr left;
		public final Token operator;
		public final Expr right;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Binary binary = (Binary) o;
            return Objects.equals(left, binary.left) && Objects.equals(operator, binary.operator) && Objects.equals(right, binary.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, operator, right);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitBinary(this);
        }
    }

    /** Represents a grouping of operations using parenthesis. */
    public static class Grouping extends Expr {
        public Grouping(Expr expression) {
            this.expression = expression;
        }

        public final Expr expression;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Grouping grouping = (Grouping) o;
            return Objects.equals(expression, grouping.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(expression);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitGrouping(this);
        }
    }

    /** Represents a function or distribution call.
     * <br>
     * The function itself can be represented by any expression.
     * This means that for `readData().getValue()`, `function`
     * corresponds to `readData().getValue`.
     * <br>
     * Arguments are either of type {@link Expr.AssignedArgument}
     * (for `exp(mean = someValue)`) or {@link Expr.DrawnArgument}
     * (for `exp(mean ~ someDist)`).*/
    public static class Call extends Expr {
        public Call(Expr function, Argument... arguments) {
            this.function = function;
            this.arguments = arguments;
        }

        public final Expr function;
        public final Argument[] arguments;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Call call = (Call) o;
            return Objects.equals(function, call.function) && Objects.deepEquals(arguments, call.arguments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(function, Arrays.hashCode(arguments));
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitCall(this);
        }
    }

    /** The base class for function arguments. */
    public static abstract class Argument extends Expr {
        public String name;
        public Expr expression;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Argument that = (Argument) o;
            return Objects.equals(name, that.name) && Objects.equals(expression, that.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, expression);
        }
    }

    /** Represents an assigned argument (e.g. `log(mean=1)`). If the
     * argument name is omitted (`log(1)`), then `name` is null.  */
    public static class AssignedArgument extends Argument {
        public AssignedArgument(Expr expression) {
            this.name = null; // argument name is omitted
            this.expression = expression;
        }

        public AssignedArgument(String name, Expr expression) {
            this.name = name;
            this.expression = expression;
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitAssignedArgument(this);
        }
    }

    /** Represents a drawn argument (e.g. `log(mean~someDistribution)`). The
     * argument can never be omitted for this type of argument. */
    public static class DrawnArgument extends Argument {
        public DrawnArgument(String name, Expr expression) {
            this.name = name;
            this.expression = expression;
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitDrawnArgument(this);
        }
    }

    /** Represents an array. */
    public static class Array extends Expr {
        public Array(List<Expr> elements) {
            this.elements = elements;
        }

        public final List<Expr> elements;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Array array = (Array) o;
            return Objects.equals(elements, array.elements);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(elements);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitArray(this);
        }
    }

    /** Represents an accessor (`readData().nchars`) or method call
     * (`readData().getNChars()`). The left-hand side (`readData()`)
     * can be an arbitrary expression, whereas the right-hand side
     * (`nchars`) is always a string.
     * Look at {@link Expr.Call} to see how method calls are parsed. */
    public static class Get extends Expr {
        public Get(Expr object, String propery) {
            this.object = object;
            this.propery = propery;
        }

        public final Expr object;
        public final String propery;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Get get = (Get) o;
            return Objects.equals(object, get.object) && Objects.equals(propery, get.propery);
        }

        @Override
        public int hashCode() {
            return Objects.hash(object, propery);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitGet(this);
        }
    }

}
