package org.phylospec.ast;

public class AstPrinter implements AstVisitor<String> {
    @Override
    public String visitDecoratedStmt(Stmt.Decorated stmt) {
        return "(@ " + stmt.decorator.accept(this) + " " + stmt.statememt.accept(this) + ")";
    }

    @Override
    public String visitAssignment(Stmt.Assignment stmt) {
        return "(AS " + stmt.type.accept(this) + " " + stmt.name + " " + stmt.expression.accept(this) + ")";
    }

    @Override
    public String visitDraw(Stmt.Draw stmt) {
        return "(DR " + stmt.type.accept(this) + " " + stmt.name + " " + stmt.expression.accept(this) + ")";
    }

    @Override
    public String visitLiteral(Expr.Literal expr) {
        if (expr.value instanceof String) {
            return "\"" + expr.value.toString() + "\"";
        }
        return expr.value.toString();
    }

    @Override
    public String visitVariable(Expr.Variable expr) {
        return expr.variable;
    }

    @Override
    public String visitUnary(Expr.Unary expr) {
        return "(" + expr.operator.lexeme + " " + expr.right.accept(this) + ")";
    }

    @Override
    public String visitBinary(Expr.Binary expr) {
        return "(" + expr.operator.lexeme + " " + expr.left.accept(this) + " " + expr.right.accept(this) + ")";
    }

    @Override
    public String visitCall(Expr.Call expr) {
        String result = "(CA " + expr.function.accept(this) + " ";

        for (int i = 0; i < expr.arguments.length; i++) {
            result += expr.arguments[i].accept(this);
            if (i < expr.arguments.length - 1) {
                result += " ";
            }
        }

        return result + ")";
    }

    @Override
    public String visitAssignedArgument(Expr.AssignedArgument expr) {
        return "(AA " + expr.name + " " + expr.expression.accept(this) + ")";
    }

    @Override
    public String visitDrawnArgument(Expr.DrawnArgument expr) {
        return "(DA " + expr.name + " " + expr.expression.accept(this) + ")";
    }

    @Override
    public String visitGrouping(Expr.Grouping expr) {
        return "(GR " + expr.expression.accept(this) + ")";
    }

    @Override
    public String visitArray(Expr.Array expr) {
        String result = "(ARR ";

        for (int i = 0; i < expr.elements.size(); i++) {
            result += expr.elements.get(i).accept(this);
            if (i < expr.elements.size() - 1) {
                result += " ";
            }
        }

        result += ")";
        return result;
    }

    @Override
    public String visitGet(Expr.Get expr) {
        return "(GET " + expr.object.accept(this) + " " + expr.propery + ")";
    }

    @Override
    public String visitAtomicType(Type.Atomic expr) {
        return expr.name;
    }

    @Override
    public String visitGenericType(Type.Generic expr) {
        String result = expr.name + "<";

        for (int i = 0; i < expr.typeParameters.length; i++) {
            result += expr.typeParameters[i].accept(this);
            if (i < expr.typeParameters.length - 1) {
                result += " ";
            }
        }

        result += ">";
        return result;
    }
}
