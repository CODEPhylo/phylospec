package org.phylospec.ast;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.*;
import org.phylospec.lexer.TokenType;

/**
 * Expressions are a type of node in the AST tree. This class has a number of
 * subclasses for different types of expressions like {@link Expr.Variable} or
 * {@link Expr.Call}.
 * Expressions are always part of a {@link Stmt}.
 */
public abstract class Expr extends AstNode {

    public abstract <S, E, T> E accept(AstVisitor<S, E, T> visitor);

    /** Represents a variable. Function and distribution names are also treated
     * as variables. */
    public static class Variable extends Expr {
        public Variable(String variableName) {
            this.variableName = variableName;
        }

        @JsonPropertyDescription("The name of the variable.")
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

    /** Represents a template variable. This is not used in normal PhyloSpec models, but in PhyloSpec
     * templates which have template variables which are not defined in the PhyloSpec template. */
    public static class TemplateVariable extends Expr {
        public TemplateVariable(String variableName) {
            this.variableName = variableName;
        }

        @JsonPropertyDescription("The template name of the variable.")
        public final String variableName;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            TemplateVariable variable1 = (TemplateVariable) o;
            return Objects.equals(variableName, variable1.variableName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(variableName);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitTemplateVariable(this);
        }
    }

    /** Represents an optional template variable. This is not used in normal PhyloSpec models, but in PhyloSpec
     * templates which have template variables which are not defined in the PhyloSpec template. */
    public static class OptionalTemplateVariable extends Expr {
        public OptionalTemplateVariable(String variableName) {
            this.variableName = variableName;
        }

        @JsonPropertyDescription("The template name of the variable.")
        public final String variableName;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            TemplateVariable variable1 = (TemplateVariable) o;
            return Objects.equals(variableName, variable1.variableName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(variableName);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitOptionalTemplateVariable(this);
        }
    }

    /** Represents a string template that may contain interpolated expressions,
     * e.g. {@code "file_${seed}.nex"}. Plain strings with no interpolations
     * are represented by {@link Literal} instead.
     * A string template is composed into {@link StringPart} and {@link ExpressionPart} objects. */
    public static class StringTemplate extends Expr {

        /** A single piece of a string template, either literal text or an interpolated expression. */
        public sealed interface Part permits StringTemplate.StringPart, StringTemplate.ExpressionPart {}

        public record StringPart(String value) implements Part {}

        public record ExpressionPart(Expr.Variable expression) implements Part {}

        public StringTemplate(List<Part> parts) {
            this.parts = parts;
        }

        @JsonPropertyDescription(
                "The parts of the string template, alternating between literal text and interpolated expressions.")
        public final List<Part> parts;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            StringTemplate that = (StringTemplate) o;
            return Objects.equals(parts, that.parts);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(parts);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitStringTemplate(this);
        }
    }

    /** Represents a literal, i.e. either a String, an Integer, or a
     * Real. */
    public static class Literal extends Expr {
        public Literal(Object value) {
            this.value = value;
        }

        public Literal(Object value, Unit unit) {
            this.value = value;
            this.unit = unit;
        }

        // TODO: make this type generic
        @JsonPropertyDescription("The literal value (either a string, a number, or a boolean).")
        public Object value;

        public Unit unit = Unit.IMPLICIT;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Literal literal = (Literal) o;
            return Objects.equals(value, literal.value) && unit == literal.unit;
        }

        public void attachUnit(Unit unit) {
            this.unit = unit;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, unit);
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

        @JsonPropertyDescription("The unary operation (either MINUS or BANG).")
        public final TokenType operator;

        @JsonPropertyDescription("The expression to which the unary operation is applied to.")
        public Expr right;

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

        @JsonPropertyDescription("The LHS expression to which the binary operation is applied to.")
        public Expr left;

        @JsonPropertyDescription(
                "The unary operation (either PLUS, MINUS, STAR, SLASH, BANG_EQUAL, EQUAL_EQUAL, GREATER_EQUAL or LESS_EQUAL).")
        public final TokenType operator;

        @JsonPropertyDescription("The RHS expression to which the binary operation is applied to.")
        public Expr right;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Binary binary = (Binary) o;
            return Objects.equals(left, binary.left)
                    && Objects.equals(operator, binary.operator)
                    && Objects.equals(right, binary.right);
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

        @JsonPropertyDescription("The expression being grouped.")
        public Expr expression;

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

        @JsonPropertyDescription("The function name prefixed by its namespace.")
        public String functionName;

        @JsonPropertyDescription("The passed arguments.")
        public final Argument[] arguments;

        /**
         * Maps the passed arguments onto the parameters declared by the callee.
         * An argument name can only be omitted in two cases: if the passed expression is a
         * variable with the same name as a parameter, or if this is the only passed argument
         * and the callee declares exactly one required parameter.
         * @throws ArgumentResolutionError if an argument cannot be bound to exactly one parameter
         */
        public Map<String, Argument> resolveArgumentNames(List<Parameter> parameters) {
            Set<String> parameterNames =
                    parameters.stream().map(Parameter::name).collect(toSet());
            List<Parameter> requiredParameters =
                    parameters.stream().filter(Parameter::required).toList();

            // we bind every argument we can and remember the first failure of each kind, because
            // we report the failures in a fixed order of relevance rather than in argument order

            Map<String, Argument> boundArguments = new LinkedHashMap<>();
            ArgumentResolutionError missingNameError = null;
            ArgumentResolutionError duplicateNameError = null;
            ArgumentResolutionError unknownNameError = null;

            for (Argument argument : arguments) {
                String parameterName = resolveArgumentName(argument, parameterNames, requiredParameters);

                if (parameterName == null) {
                    // the argument name is omitted where it cannot be inferred
                    if (missingNameError == null) missingNameError = new ArgumentResolutionError.MissingName(argument);
                } else if (!parameterNames.contains(parameterName)) {
                    if (unknownNameError == null) {
                        unknownNameError =
                                new ArgumentResolutionError.UnknownName(argument, parameterName, parameterNames);
                    }
                } else if (boundArguments.containsKey(parameterName)) {
                    if (duplicateNameError == null) {
                        duplicateNameError = new ArgumentResolutionError.DuplicateName(argument, parameterName);
                    }
                } else {
                    boundArguments.put(parameterName, argument);
                }
            }

            // report the failures, most relevant first

            if (missingNameError != null) throw missingNameError;
            if (duplicateNameError != null) throw duplicateNameError;
            if (unknownNameError != null) throw unknownNameError;

            List<String> missingRequiredNames = requiredParameters.stream()
                    .map(Parameter::name)
                    .filter(name -> !boundArguments.containsKey(name))
                    .toList();
            if (!missingRequiredNames.isEmpty()) {
                throw new ArgumentResolutionError.MissingRequired(missingRequiredNames.getFirst());
            }

            return boundArguments;
        }

        /**
         * Returns the name of the parameter the given argument binds to, or null if the argument
         * name is omitted where it cannot be inferred.
         */
        private String resolveArgumentName(
                Argument argument, Set<String> parameterNames, List<Parameter> requiredParameters) {
            if (argument.name != null) {
                // the argument name is given explicitly
                return argument.name;
            }

            if (argument.expression instanceof Variable variable) {
                // a variable names its own argument, unless it matches no parameter and there is
                // a single required one to take it
                if (!parameterNames.contains(variable.variableName)
                        && arguments.length == 1
                        && requiredParameters.size() == 1) {
                    return requiredParameters.getFirst().name();
                }
                return variable.variableName;
            }

            if (arguments.length == 1 && requiredParameters.size() == 1) {
                // the single passed value goes to the single required parameter
                return requiredParameters.getFirst().name();
            }

            return null;
        }

        /**
         * One parameter declared by the callee, used for argument resolution.
         */
        public record Parameter(String name, boolean required) {}

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
    public abstract static class Argument extends Expr {
        @JsonPropertyDescription(
                "The argument name. Null if the argument name is not specified for the first and only argument.")
        public String name;

        @JsonPropertyDescription("The expression passed to the argument.")
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

        @JsonPropertyDescription("The elements of the array.")
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

    /** Represents an index access (e.g. `x[1]` or `data[1]["header"]`). */
    public static class Index extends Expr {
        public Index(Expr object, List<Expr> indices) {
            this.object = object;
            this.indices = indices;
        }

        @JsonPropertyDescription("The expression being indexed.")
        public Expr object;

        @JsonPropertyDescription("The index expressions.")
        public List<Expr> indices;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Index that = (Index) o;
            return Objects.equals(object, that.object) && Objects.equals(indices, that.indices);
        }

        @Override
        public int hashCode() {
            return Objects.hash(object, indices);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitIndex(this);
        }
    }

    /** Represents a range (`1:num(taxa)`). */
    public static class Range extends Expr {
        public Range(Expr from, Expr to) {
            this.from = from;
            this.to = to;
        }

        @JsonPropertyDescription("The lower bound of the range (inclusive).")
        public Expr from;

        @JsonPropertyDescription("The upper bound of the range (inclusive).")
        public Expr to;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Range range = (Range) o;
            return Objects.equals(from, range.from) && Objects.equals(to, range.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        @Override
        public <S, E, T> E accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitRange(this);
        }
    }
}
