package org.phylospec.components;

import static org.phylospec.typeresolver.properties.TypePropertyNames.describeProperty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a comparison between a property of a component input and either the property of
 * another component input or a constant. Examples are `tree.numBranches == branchRates.num`
 * and `baseFrequencies.num == 4`. Such constraints can be used to formalize conditions that
 * have to be met such that a generator can be applied.
 * The constraints are usually given as strings by the JSON component definitions. This
 * class can be used to parse these strings.
 */
public abstract sealed class ParsedTypeConstraint
        permits ParsedTypeConstraint.PropertyComparison, ParsedTypeConstraint.ConstantComparison {
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*"
            + "(==|!=|>=|<=|=<|>|<)\\s*"
            + "(?:([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)"
            + "|([+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)))\\s*$");

    private final ConstraintType constraintType;
    private final String leftInputName;
    private final String leftPropertyName;

    private ParsedTypeConstraint(ConstraintType constraintType, String leftInputName, String leftPropertyName) {
        this.constraintType = constraintType;
        this.leftInputName = leftInputName;
        this.leftPropertyName = leftPropertyName;
    }

    /**
     * Parses a comparison of an input property with another input property or with a constant.
     *
     * @param constraint the constraint to parse
     * @return a {@link PropertyComparison} or a {@link ConstantComparison}, depending on the right side
     * @throws IllegalArgumentException if the constraint does not compare an input property with
     *     another input property or a constant
     */
    public static ParsedTypeConstraint parse(String constraint) {
        Matcher matcher = CONSTRAINT_PATTERN.matcher(constraint);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Type constraint must compare an input property with an input property or a number: " + constraint);
        }

        ConstraintType constraintType = parseConstraintType(matcher.group(3));
        String leftInputName = matcher.group(1);
        String leftPropertyName = matcher.group(2);

        String constant = matcher.group(6);
        if (constant != null) {
            return new ConstantComparison(
                    constraintType, leftInputName, leftPropertyName, Double.parseDouble(constant));
        }

        return new PropertyComparison(
                constraintType, leftInputName, leftPropertyName, matcher.group(4), matcher.group(5));
    }

    private static ConstraintType parseConstraintType(String operator) {
        return switch (operator) {
            case "==" -> ConstraintType.EQUALITY;
            case "!=" -> ConstraintType.INEQUALITY;
            case "<" -> ConstraintType.LESS;
            case "<=", "=<" -> ConstraintType.LESS_THAN;
            case ">" -> ConstraintType.GREATER;
            case ">=" -> ConstraintType.GREATER_THAN;
            default -> throw new IllegalArgumentException("Unsupported type constraint operator: " + operator);
        };
    }

    /**
     * Returns the comparison performed by this constraint.
     *
     * @return the constraint type
     */
    public ConstraintType getConstraintType() {
        return constraintType;
    }

    /**
     * Returns the input name on the left side of the comparison.
     *
     * @return the left input name
     */
    public String getLeftInputName() {
        return leftInputName;
    }

    /**
     * Returns the property name on the left side of the comparison.
     *
     * @return the left property name
     */
    public String getLeftPropertyName() {
        return leftPropertyName;
    }

    /**
     * Returns a human-readable error message.
     *
     * @return the message
     */
    public String errorMessage() {
        return describeProperty(leftInputName, leftPropertyName, true)
                + " must "
                + describeComparison(constraintType)
                + " "
                + describeRightSide()
                + ".";
    }

    /**
     * Describes the right side of the comparison in a human-readable way.
     *
     * @return the description
     */
    protected abstract String describeRightSide();

    private static String describeComparison(ConstraintType constraintType) {
        return switch (constraintType) {
            case EQUALITY -> "be equal to";
            case INEQUALITY -> "be different from";
            case LESS -> "be less than";
            case LESS_THAN -> "be less than or equal to";
            case GREATER -> "be greater than";
            case GREATER_THAN -> "be greater than or equal to";
        };
    }

    /**
     * Represents a comparison of two input properties, such as `tree.numBranches == branchRates.num`.
     */
    public static final class PropertyComparison extends ParsedTypeConstraint {
        private final String rightInputName;
        private final String rightPropertyName;

        private PropertyComparison(
                ConstraintType constraintType,
                String leftInputName,
                String leftPropertyName,
                String rightInputName,
                String rightPropertyName) {
            super(constraintType, leftInputName, leftPropertyName);
            this.rightInputName = rightInputName;
            this.rightPropertyName = rightPropertyName;
        }

        /**
         * Returns the input name on the right side of the comparison.
         *
         * @return the right input name
         */
        public String getRightInputName() {
            return rightInputName;
        }

        /**
         * Returns the property name on the right side of the comparison.
         *
         * @return the right property name
         */
        public String getRightPropertyName() {
            return rightPropertyName;
        }

        @Override
        protected String describeRightSide() {
            return describeProperty(rightInputName, rightPropertyName, false);
        }
    }

    /**
     * Represents a comparison of an input property with a constant, such as `baseFrequencies.num == 4`.
     */
    public static final class ConstantComparison extends ParsedTypeConstraint {
        private final double constant;

        private ConstantComparison(
                ConstraintType constraintType, String leftInputName, String leftPropertyName, double constant) {
            super(constraintType, leftInputName, leftPropertyName);
            this.constant = constant;
        }

        /**
         * Returns the constant on the right side of the comparison.
         *
         * @return the constant
         */
        public double getConstant() {
            return constant;
        }

        @Override
        protected String describeRightSide() {
            // print whole numbers without a trailing `.0`

            if (constant == Math.rint(constant) && !Double.isInfinite(constant)) {
                return Long.toString((long) constant);
            }

            return Double.toString(constant);
        }
    }

    /**
     * Identifies the comparison operator used by a parsed constraint.
     */
    public enum ConstraintType {
        EQUALITY,
        INEQUALITY,
        LESS,
        LESS_THAN,
        GREATER,
        GREATER_THAN
    }
}
