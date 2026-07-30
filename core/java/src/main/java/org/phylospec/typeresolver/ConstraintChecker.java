package org.phylospec.typeresolver;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.components.ParsedTypeConstraint;
import org.phylospec.errors.Error;

public class ConstraintChecker {

    public static void checkConstraints(
            AstNode astNode,
            List<String> constraintStrings,
            TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication)
            throws Error {
        Map<String, Set<ResolvedType>> resolvedArguments =
                resolvedGeneratorApplication.resolvedArguments();

        for (String constraintString : constraintStrings) {
            ParsedTypeConstraint constraint = new ParsedTypeConstraint(constraintString);

            Set<ResolvedType> leftInputTypeSet =
                    resolvedArguments.get(constraint.getLeftInputName());
            Set<ResolvedType> rightInputTypeSet =
                    resolvedArguments.get(constraint.getRightInputName());

            if (leftInputTypeSet == null || rightInputTypeSet == null) {
                // we don't know about this input
                // let's ignore this constraint
                continue;
            }

            for (ResolvedType leftInputType : leftInputTypeSet) {
                for (ResolvedType rightInputType : rightInputTypeSet) {
                    Object leftProperty =
                            leftInputType.getProperty(constraint.getLeftPropertyName());
                    Object rightProperty =
                            rightInputType.getProperty(constraint.getRightPropertyName());

                    if (!(leftProperty instanceof Number leftNr)
                            || !(rightProperty instanceof Number rightNr)) {
                        // these are somehow not numbers, or we don't know these properties
                        return;
                    }

                    boolean fulfilled =
                            switch (constraint.getConstraintType()) {
                                case EQUALITY -> leftNr.equals(rightNr);
                                case INEQUALITY -> !leftNr.equals(rightNr);
                                case LESS -> leftNr.doubleValue() < rightNr.doubleValue();
                                case LESS_THAN -> leftNr.doubleValue() <= rightNr.doubleValue();
                                case GREATER -> leftNr.doubleValue() > rightNr.doubleValue();
                                case GREATER_THAN -> leftNr.doubleValue() >= rightNr.doubleValue();
                            };

                    if (fulfilled) {
                        return;
                    }
                }
            }

            // all type combinations could be evaluated and none of them were successful

            throw new Error(astNode.getRange(), "Check not passed.", constraintString);
        }
    }
}
