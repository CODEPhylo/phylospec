package patternmatching;

import org.phylospec.ast.*;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.VariableResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AstTemplateMatcher implements AstVisitor<Void, Void, Void> {

    private final AstNode templateRoot;
    private final VariableResolver templateVariableResolver;

    private final Map<String, AstNode> templateVariableMap;

    private VariableResolver queryVariableResolver;
    private AstNode currentQueryNode = null;

    public AstTemplateMatcher(String phyloSpecTemplate) {
        List<Token> tokens = new Lexer(phyloSpecTemplate).scanTokens();
        List<AstNode> statements = new Parser(tokens).parseStmtOrExpr();

        // make sure all but the last statement are actual Stmt nodes and not just expressions
        for (int i = 0; i < statements.size() - 1; i++) {
            if (!(statements.get(i) instanceof Stmt)) {
                throw new IllegalArgumentException("The PhyloSpec template contains an expression on the " + (i + 1) + "-th line. Only the last line can contain expressions, all other have to contain complete statements like assignments or draws.");
            }
        }

        this.templateRoot = statements.getLast();
        this.templateVariableResolver = new VariableResolver(statements);
        this.templateVariableMap = new HashMap<>();
    }

    public Map<String, AstNode> match(AstNode query, VariableResolver queryVariableResolver) {
        this.currentQueryNode = query;
        this.queryVariableResolver = queryVariableResolver;
        try {
            this.match(this.templateRoot);
            return this.templateVariableMap;
        } catch (MatchingError error) {
            return null;
        }
    }

    @Override
    public Void visitDecoratedStmt(Stmt.Decorated stmt) {
        if (!(currentQueryNode instanceof Stmt.Decorated queryStmt)) {
            throw new MatchingError();
        }

        currentQueryNode = goQy(queryStmt.decorator);
        stmt.decorator.accept(this);

        currentQueryNode = goQy(queryStmt.statement);
        stmt.statement.accept(this);

        return null;
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        if (!(currentQueryNode instanceof Stmt.Assignment queryStmt)) {
            throw new MatchingError();
        }

        this.check(stmt.name.equals(queryStmt.name));

        currentQueryNode = goQy(queryStmt.type);
        stmt.type.accept(this);

        currentQueryNode = goQy(queryStmt.expression);
        stmt.expression.accept(this);

        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        if (!(currentQueryNode instanceof Stmt.Draw queryStmt)) {
            throw new MatchingError();
        }

        this.check(stmt.name.equals(queryStmt.name));

        currentQueryNode = goQy(queryStmt.type);
        stmt.type.accept(this);

        currentQueryNode = goQy(queryStmt.expression);
        stmt.expression.accept(this);

        return null;
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        if (!(currentQueryNode instanceof Stmt.Import queryStmt)) {
            throw new MatchingError();
        }

        this.check(stmt.namespace.size() == queryStmt.namespace.size());

        for (int i = 0; i < stmt.namespace.size(); i++) {
            this.check(stmt.namespace.get(i).equals(queryStmt.namespace.get(i)));
        }

        return null;
    }

    @Override
    public Void visitIndexedStmt(Stmt.Indexed indexed) {
        if (!(currentQueryNode instanceof Stmt.Indexed queryIndexed)) {
            throw new MatchingError();
        }

        this.check(indexed.indices.size() == queryIndexed.indices.size());
        this.check(indexed.ranges.size() == queryIndexed.ranges.size());

        for (int i = 0; i < indexed.indices.size(); i++) {
            this.check(indexed.indices.get(i).variableName.equals(queryIndexed.indices.get(i).variableName));
        }

        for (int i = 0; i < indexed.ranges.size(); i++) {
            currentQueryNode = goQy(queryIndexed.ranges.get(i));
            match(indexed.ranges.get(i));
        }

        currentQueryNode = goQy(queryIndexed.statement);
        match(indexed.statement);

        return null;
    }

    @Override
    public Void visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        if (!(currentQueryNode instanceof Stmt.ObservedAs queryObservedAs)) {
            throw new MatchingError();
        }

        currentQueryNode = goQy(queryObservedAs.stmt);
        match(observedAs.stmt);

        currentQueryNode = goQy(queryObservedAs.observedAs);
        match(observedAs.observedAs);

        return null;
    }

    @Override
    public Void visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        if (!(currentQueryNode instanceof Stmt.ObservedBetween queryObservedBetween)) {
            throw new MatchingError();
        }

        currentQueryNode = goQy(queryObservedBetween.stmt);
        match(observedBetween.stmt);

        currentQueryNode = goQy(queryObservedBetween.observedFrom);
        match(observedBetween.observedFrom);

        currentQueryNode = goQy(queryObservedBetween.observedTo);
        match(observedBetween.observedTo);

        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        if (!(currentQueryNode instanceof Expr.Literal queryLiteral)) {
            throw new MatchingError();
        }

        this.check(expr.value.equals(queryLiteral.value));
        this.check(expr.unit == queryLiteral.unit);

        return null;
    }

    @Override
    public Void visitStringTemplate(Expr.StringTemplate expr) {
        if (!(currentQueryNode instanceof Expr.StringTemplate queryTemplate)) {
            throw new MatchingError();
        }

        this.check(expr.parts.size() == queryTemplate.parts.size());

        for (int i = 0; i < expr.parts.size(); i++) {
            Expr.StringTemplate.Part templatePart = expr.parts.get(i);
            Expr.StringTemplate.Part queryPart = queryTemplate.parts.get(i);

            if (templatePart instanceof Expr.StringTemplate.StringPart(String value)) {
                this.check(queryPart instanceof Expr.StringTemplate.StringPart(String value1) && value.equals(value1));
            } else if (templatePart instanceof Expr.StringTemplate.ExpressionPart(Expr.Variable expression)) {
                this.check(queryPart instanceof Expr.StringTemplate.ExpressionPart);
                currentQueryNode = goQy(((Expr.StringTemplate.ExpressionPart) queryPart).expression());
                match(expression);
            }
        }

        return null;
    }

    @Override
    public Void visitVariable(Expr.Variable expr) {
        if (!(currentQueryNode instanceof Expr.Variable queryVariable)) {
            throw new MatchingError();
        }

        this.check(expr.variableName.equals(queryVariable.variableName));

        return null;
    }

    @Override
    public Void visitTemplateVariable(Expr.TemplateVariable expr) {
        // template variables match any expression and capture the query node
        templateVariableMap.put(expr.variableName, currentQueryNode);
        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        if (!(currentQueryNode instanceof Expr.Unary queryUnary)) {
            throw new MatchingError();
        }

        this.check(expr.operator == queryUnary.operator);

        currentQueryNode = goQy(queryUnary.right);
        match(expr.right);

        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        if (!(currentQueryNode instanceof Expr.Binary queryBinary)) {
            throw new MatchingError();
        }

        this.check(expr.operator == queryBinary.operator);

        currentQueryNode = goQy(queryBinary.left);
        match(expr.left);

        currentQueryNode = goQy(queryBinary.right);
        match(expr.right);

        return null;
    }

    @Override
    public Void visitCall(Expr.Call expr) {
        if (!(currentQueryNode instanceof Expr.Call queryCall)) {
            throw new MatchingError();
        }

        this.check(expr.functionName.equals(queryCall.functionName));
        this.check(expr.arguments.length == queryCall.arguments.length);

        for (int i = 0; i < expr.arguments.length; i++) {
            currentQueryNode = goQy(queryCall.arguments[i]);
            match(expr.arguments[i]);
        }

        return null;
    }

    @Override
    public Void visitAssignedArgument(Expr.AssignedArgument expr) {
        if (!(currentQueryNode instanceof Expr.AssignedArgument queryArg)) {
            throw new MatchingError();
        }

        this.check(Objects.equals(expr.name, queryArg.name));

        currentQueryNode = goQy(queryArg.expression);
        match(expr.expression);

        return null;
    }

    @Override
    public Void visitDrawnArgument(Expr.DrawnArgument expr) {
        if (!(currentQueryNode instanceof Expr.DrawnArgument queryArg)) {
            throw new MatchingError();
        }

        this.check(Objects.equals(expr.name, queryArg.name));

        currentQueryNode = goQy(queryArg.expression);
        match(expr.expression);

        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        if (!(currentQueryNode instanceof Expr.Grouping queryGrouping)) {
            throw new MatchingError();
        }

        currentQueryNode = goQy(queryGrouping.expression);
        match(expr.expression);

        return null;
    }

    @Override
    public Void visitArray(Expr.Array expr) {
        if (!(currentQueryNode instanceof Expr.Array queryArray)) {
            throw new MatchingError();
        }

        this.check(expr.elements.size() == queryArray.elements.size());

        for (int i = 0; i < expr.elements.size(); i++) {
            currentQueryNode = goQy(queryArray.elements.get(i));
            match(expr.elements.get(i));
        }

        return null;
    }

    @Override
    public Void visitIndex(Expr.Index expr) {
        if (!(currentQueryNode instanceof Expr.Index queryIndex)) {
            throw new MatchingError();
        }

        this.check(expr.indices.size() == queryIndex.indices.size());

        currentQueryNode = goQy(queryIndex.object);
        match(expr.object);

        for (int i = 0; i < expr.indices.size(); i++) {
            currentQueryNode = goQy(queryIndex.indices.get(i));
            match(expr.indices.get(i));
        }

        return null;
    }

    @Override
    public Void visitRange(Expr.Range range) {
        if (!(currentQueryNode instanceof Expr.Range queryRange)) {
            throw new MatchingError();
        }

        currentQueryNode = goQy(queryRange.from);
        match(range.from);

        currentQueryNode = goQy(queryRange.to);
        match(range.to);

        return null;
    }

    @Override
    public Void visitAtomicType(AstType.Atomic expr) {
        if (!(currentQueryNode instanceof AstType.Atomic queryAtomic)) {
            throw new MatchingError();
        }

        this.check(expr.name.equals(queryAtomic.name));

        return null;
    }

    @Override
    public Void visitGenericType(AstType.Generic expr) {
        if (!(currentQueryNode instanceof AstType.Generic queryGeneric)) {
            throw new MatchingError();
        }

        this.check(expr.name.equals(queryGeneric.name));
        this.check(expr.typeParameters.length == queryGeneric.typeParameters.length);

        for (int i = 0; i < expr.typeParameters.length; i++) {
            currentQueryNode = goQy(queryGeneric.typeParameters[i]);
            match(expr.typeParameters[i]);
        }

        return null;
    }

    private void check(boolean predicate) {
        if (!predicate) throw new MatchingError();
    }

    private void match(AstNode template) {
        template = this.goTmp(template);
        if (template instanceof Stmt node) node.accept(this);
        else if (template instanceof Expr node) node.accept(this);
        else if (template instanceof AstType node) node.accept(this);
    }

    private AstNode goQy(AstNode node) {
        return go(node, this.queryVariableResolver);
    }

    private AstNode goTmp(AstNode node) {
        return go(node, this.templateVariableResolver);
    }

    private AstNode go(AstNode node, VariableResolver variableResolver) {
        if (node instanceof Expr.Variable variable) {
            // we try to pass through the variable
            AstNode resolved = variableResolver.resolveVariable(variable);
            return Objects.requireNonNullElse(go(resolved, variableResolver), node);
        }
        if (node instanceof Stmt.Decorated decorated) {
            return go(decorated.statement, variableResolver);
        }
        return node;
    }

    private static class MatchingError extends RuntimeException {
    }
}
