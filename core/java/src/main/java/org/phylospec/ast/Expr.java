package org.phylospec.ast;

import org.phylospec.lexer.TokenRange;
import org.phylospec.lexer.TokenType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Expressions are a type of node in the AST tree. This class has a number of
 * subclasses for different types of expressions like {@link Expr.Variable} or
 * {@link Expr.Call}.
 * Expressions are always part of a {@link Stmt}.
 */
public abstract class Expr extends AstNode {

    abstract public <S, E, T> E accept(AstVisitor<S, E, T> visitor);

    public TokenRange tokenRange = null;

    /** Represents a variable. Function and distribution names are also treated
     * as variables. */
    public static class Variable extends Expr {
        public Variable(String variableName) {
            this.variableName = variableName;
        }
        public Variable(String variableName, TokenRange tokenRange) {
            this.variableName = variableName;
            this.tokenRange = tokenRange;
        }

        public final String variableName;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Variable variable1 = (Variable) o;
            return Objects.equals(variableName, variable1.variableName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(variableName);
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
        public Literal(Object value, TokenRange tokenRange) {
            this.value = value;
            this.tokenRange = tokenRange;
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
		public Unary(TokenType operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}
        public Unary(TokenType operator, Expr right, TokenRange tokenRange) {
            this.operator = operator;
            this.right = right;
            this.tokenRange = tokenRange;
        }

		public final TokenType operator;
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
		public Binary(Expr left, TokenType operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}
        public Binary(Expr left, TokenType operator, Expr right, TokenRange tokenRange) {
			this.left = left;
			this.operator = operator;
			this.right = right;
            this.tokenRange = tokenRange;
		}

		public final Expr left;
		public final TokenType operator;
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
     * The function itself is represented by its name.
     * This means that methods like `readData().getValue()` are not
     * supported.
     * <br>
     * Arguments are either of type {@link Expr.AssignedArgument}
     * (for `exp(mean = someValue)`) or {@link Expr.DrawnArgument}
     * (for `exp(mean ~ someDist)`).*/
    public static class Call extends Expr {
        public Call(String functionName, Argument... arguments) {
            this.functionName = functionName;
            this.arguments = arguments;
        }
        public Call(String functionName, TokenRange tokenRange, Argument... arguments) {
            this.functionName = functionName;
            this.arguments = arguments;
            this.tokenRange = tokenRange;
        }

        public final String functionName;
        public final Argument[] arguments;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Call call = (Call) o;
            return Objects.equals(functionName, call.functionName) && Objects.deepEquals(arguments, call.arguments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(functionName, Arrays.hashCode(arguments));
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
        public Get(Expr object, String properyName) {
            this.object = object;
            this.properyName = properyName;
        }
        public Get(Expr object, String properyName, TokenRange tokenRange) {
            this.object = object;
            this.properyName = properyName;
            this.tokenRange = tokenRange;
        }

        public final Expr object;
        public final String properyName;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Get get = (Get) o;
            return Objects.equals(object, get.object) && Objects.equals(properyName, get.properyName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(object, properyName);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitGet(this);
        }
    }

}
