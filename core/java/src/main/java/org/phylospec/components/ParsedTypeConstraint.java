package org.phylospec.components;

import static org.phylospec.typeresolver.properties.TypePropertyNames.describeProperty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a comparison between properties of two component inputs. An example is
 * `tree.numBranches == branchRates.num`. Such constraints can be used to formalize
 * conditions that have to be met such that a generator can be applied.
 * The constraints are usually given as strings by the JSON component definitions. This
 * class can be used to parse these strings.
 */
public class ParsedTypeConstraint {
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("^\\s*\\$?([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*"
            + "(==|!=|>=|<=|=<|>|<)\\s*"
            + "\\$?([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*$");

    private final ConstraintType constraintType;
    private final String leftInputName;
    private final String leftPropertyName;
    private final String rightInputName;
    private final String rightPropertyName;

    /**
     * Parses a comparison between two input property references.
     *
     * @param constraint the constraint to parse
     * @throws IllegalArgumentException if the constraint does not compare two input properties
     */
    public ParsedTypeConstraint(String constraint) {
        Matcher matcher = CONSTRAINT_PATTERN.matcher(constraint);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Type constraint must compare two input properties: " + constraint);
        }

        this.leftInputName = matcher.group(1);
        this.leftPropertyName = matcher.group(2);
        this.constraintType = parseConstraintType(matcher.group(3));
        this.rightInputName = matcher.group(4);
        this.rightPropertyName = matcher.group(5);
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
                + describeProperty(rightInputName, rightPropertyName, false)
                + ".";
    }

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
