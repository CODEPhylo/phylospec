package org.phylospec.converters;

import org.phylospec.ast.*;
import org.phylospec.lexer.TokenType;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;

import java.util.*;

public class LPhyConverter implements AstVisitor<StringBuilder, StringBuilder, Void> {

    private List<String> dataStatements;
    private List<String> modelStatements;
    private StochasticityResolver stochasticityResolver;

    private LPhyConverter(List<Stmt> statements) {
        dataStatements = new ArrayList<>();
        modelStatements = new ArrayList<>();

        stochasticityResolver = new StochasticityResolver();
        for (Stmt stmt : statements) {
            stmt.accept(stochasticityResolver);
        }
    }

    public static String convertToLphy(List<Stmt> statements) {
        LPhyConverter converter = new LPhyConverter(statements);

        for (Stmt statement : statements) {
            statement.accept(converter);
        }

        StringBuilder result = new StringBuilder();

        if (!converter.dataStatements.isEmpty()) {
            result.append("data {\n");

            for (String dataStatement : converter.dataStatements) {
                result.append("\t").append(dataStatement).append("\n");
            }

            result.append("}");
        }

        if (!converter.dataStatements.isEmpty() && !converter.modelStatements.isEmpty()) {
            result.append("\n");
        }

        if (!converter.modelStatements.isEmpty()) {
            result.append("model {\n");

            for (String modelStatement : converter.modelStatements) {
                result.append("\t").append(modelStatement).append("\n");
            }

            result.append("}");
        }

        return result.toString();
    }

    @Override
    public StringBuilder visitDecoratedStmt(Stmt.Decorated stmt) {
        return null;
    }

    @Override
    public StringBuilder visitAssignment(Stmt.Assignment stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(stmt.name).append(" = ").append(stmt.expression.accept(this)).append(";");
        return remember(stmt, builder);
    }

    @Override
    public StringBuilder visitDraw(Stmt.Draw stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(stmt.name).append(" ~ ").append(stmt.expression.accept(this)).append(";");
        return remember(stmt, builder);
    }

    @Override
    public StringBuilder visitImport(Stmt.Import stmt) {
        return new StringBuilder();
    }

    @Override
    public StringBuilder visitLiteral(Expr.Literal expr) {
        return switch (expr.value) {
            case Integer value -> new StringBuilder(value.toString());
            case Float value -> new StringBuilder(value.toString());
            case String value -> new StringBuilder("\"").append(value).append("\"");
            default -> new StringBuilder(expr.value.toString());
        };
    }

    @Override
    public StringBuilder visitVariable(Expr.Variable expr) {
        return new StringBuilder(expr.variableName);
    }

    @Override
    public StringBuilder visitUnary(Expr.Unary expr) {
        if (expr.operator == TokenType.MINUS) {
            return new StringBuilder().append("-").append(expr.right.accept(this));
        }
        throw new LPhyConversionError("Unary operation " + TokenType.getLexeme(expr.operator) + " is not supported in LPhy.");
    }

    @Override
    public StringBuilder visitBinary(Expr.Binary expr) {
        StringBuilder left = expr.left.accept(this);
        StringBuilder right = expr.right.accept(this);

        if (expr.operator == TokenType.MINUS) {
            return new StringBuilder().append(left).append(" - ").append(right);
        } else if (expr.operator == TokenType.PLUS) {
            return new StringBuilder().append(left).append(" + ").append(right);
        } else if (expr.operator == TokenType.STAR) {
            return new StringBuilder().append(left).append(" * ").append(right);
        } else if (expr.operator == TokenType.SLASH) {
            return new StringBuilder().append(left).append(" / ").append(right);
        }

        throw new LPhyConversionError("Binary operation " + TokenType.getLexeme(expr.operator) + " is not supported in LPhy.");
    }

    @Override
    public StringBuilder visitCall(Expr.Call expr) {
        Map<String, String> arguments = new HashMap<>();
        for (Expr.Argument arg : expr.arguments) {
            arguments.put(arg.name, arg.accept(this).toString());
        }
        return LPhyGeneratorMapping.map(expr.functionName, arguments);
    }

    @Override
    public StringBuilder visitAssignedArgument(Expr.AssignedArgument expr) {
        return expr.expression.accept(this);
    }

    @Override
    public StringBuilder visitDrawnArgument(Expr.DrawnArgument expr) {
        return null;
    }

    @Override
    public StringBuilder visitGrouping(Expr.Grouping expr) {
        return new StringBuilder().append("(").append(expr.expression.accept(this)).append(")");
    }

    @Override
    public StringBuilder visitArray(Expr.Array expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");

        for (int i = 0; i < expr.elements.size(); i++) {
            builder.append(expr.elements.get(i).accept(this));

            if (i < expr.elements.size() - 1) {
                builder.append(", ");
            }
        }

        builder.append("]");
        return builder;
    }

    @Override
    public StringBuilder visitGet(Expr.Get expr) {
        return null;
    }

    @Override
    public Void visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    @Override
    public Void visitGenericType(AstType.Generic expr) {
        return null;
    }

    private StringBuilder remember(AstNode node, StringBuilder builder) {
        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);

        if (stochasticity == Stochasticity.DETERMINISTIC) {
            dataStatements.add(builder.toString());
        } else if (stochasticity == Stochasticity.STOCHASTIC) {
            modelStatements.add(builder.toString());
        }

        return builder;
    }

    static class LPhyConversionError extends RuntimeException {
        public LPhyConversionError(String s) {
            super(s);
        }
    }
}
