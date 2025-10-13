package org.phylospec.ast;

import org.phylospec.lexer.Token;

import java.util.Arrays;
import java.util.Objects;

public abstract class Expr {

    public static class Grouping extends Expr {
		public Grouping(Expr expression) {
			this.expression = expression;
		}

		final Expr expression;

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
    }

    public static class Variable extends Expr {
        public Variable(Token variable) {
            this.variable = variable;
        }

        public final Token variable;

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
    }

	public static class Literal extends Expr {
		public Literal(Object value) {
			this.value = value;
		}

		final Object value;

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
    }

	public static class Unary extends Expr {
		public Unary(Token operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}

		final Token operator;
		final Expr right;

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
    }

	public static class Binary extends Expr {
		public Binary(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		final Expr left;
		final Token operator;
		final Expr right;

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
    }

    public static class Call extends Expr {
        public Call(Expr function, Argument... arguments) {
            this.function = function;
            this.arguments = arguments;
        }

        final Expr function;
        final Argument[] arguments;

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
    }

    public static class Argument extends Expr {
        public Argument(String name, Expr expression) {
            this.name = name;
            this.expression = expression;
        }

        final String name;
        final Expr expression;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Argument argument = (Argument) o;
            return Objects.equals(name, argument.name) && Objects.equals(expression, argument.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, expression);
        }
    }

}
