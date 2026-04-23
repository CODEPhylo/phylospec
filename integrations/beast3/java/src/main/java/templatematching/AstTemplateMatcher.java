package templatematching;

import org.phylospec.ast.*;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.VariableResolver;

import java.util.*;

/// Matches a PhyloSpec AST query against a PhyloSpec template.
///
/// The template is a PhyloSpec snippet that may contain template variables (e.g. `$x`).
/// A match succeeds if the query AST is structurally equivalent to the template, with
/// template variables capturing the corresponding query sub-trees.
///
/// Usage:
/// ```
/// AstTemplateMatcher matcher = new AstTemplateMatcher("Real x ~ Normal(mean=$mu, sd=$sigma)");
/// Map<String, AstNode> bindings = matcher.match(queryNode, queryVariableResolver);
/// // bindings maps "$mu" -> ..., "$sigma" -> ...
/// ```
public class AstTemplateMatcher implements AstVisitor<Void, Void, Void> {

    private final AstNode templateRoot;
    private Stmt.Block templateBlock = Stmt.Block.NO_BLOCK;
    private final VariableResolver templateVariableResolver;

    // maps template variable names (e.g. "$x") to the query sub-trees they were bound to
    private final Map<String, AstNode> templateVariableMap;

    private VariableResolver queryVariableResolver;
    private AstNode currentQueryNode = null;

    // maps query index variable names to template index variable names during indexed statement matching
    private Map<String, String> currentIndexBindings;

    public AstTemplateMatcher(String phyloSpecTemplate) {
        List<Token> tokens = new Lexer(phyloSpecTemplate).scanTokens();
        List<AstNode> statements = new Parser(tokens).parseStmtOrExpr();

        // initialize the template block

        if (statements.getFirst() instanceof Stmt stmt) {
            this.templateBlock = stmt.block;
        }

        // make sure all but the last statement are actual Stmt nodes and not just expressions

        for (int i = 0; i < statements.size() - 1; i++) {
            if (!(statements.get(i) instanceof Stmt stmt)) {
                throw new IllegalArgumentException("The PhyloSpec template contains an expression on the " + (i + 1) + "-th line. Only the last line can contain expressions, all other have to contain complete statements like assignments or draws.");
            }

            if (this.templateBlock != stmt.block) {
                throw new IllegalArgumentException("The PhyloSpec template spans multiple blocks. This is not supported.");
            }
        }

        // initialize the template variables

        this.templateRoot = statements.getLast();
        this.templateVariableResolver = new VariableResolver(statements);
        this.templateVariableMap = new HashMap<>();
    }

    /**
     * Tries to match a query against the template. The query is given by the AstNode which should be matched
     * with the root entry point of the template and the query variable resolver (to match across multiple
     * statements).
     * Returns the mapping of template variables to query AstNodes on a match, or null if there is no match.
     */
    public Map<String, AstNode> match(AstNode queryRoot, VariableResolver queryVariableResolver) {
        this.queryVariableResolver = queryVariableResolver;
        this.templateVariableMap.clear();
        this.currentIndexBindings = null;

        try {
            // if the root is a statement, check its block
            // we don't check the block of any referenced statements for simplicity
            if (queryRoot instanceof Stmt queryStmt) this.matchBlock(queryStmt);

            this.match(this.templateRoot, queryRoot);
            return this.templateVariableMap;
        } catch (MatchingError error) {
            return null;
        }
    }

    /**
     * Sets the query node to the current query node and visits the template node. Passes through things like
     * variables or observed statements if appropriate.
     */
    private void match(AstNode template, AstNode query) {
        // if we have reached a template variable, we directly compare with no passthrough

        if (template instanceof Expr.TemplateVariable templateVariable) {
            this.currentQueryNode = query;
            templateVariable.accept(this);
            return;
        }
        if (template instanceof Expr.OptionalTemplateVariable templateVariable) {
            this.currentQueryNode = query;
            templateVariable.accept(this);
            return;
        }

        query = this.potentiallyPassThrough(query, this.queryVariableResolver);
        template = this.potentiallyPassThrough(template, templateVariableResolver);

        // if the query is an observed statement but the template is not, this is fine, and we pass through the observation

        if (query instanceof Stmt.ObservedAs observed && !(template instanceof Stmt.ObservedAs)) {
            this.match(template, observed.stmt);
            return;
        }
        if (query instanceof Stmt.ObservedBetween observed && !(template instanceof Stmt.ObservedBetween)) {
            this.match(template, observed.stmt);
            return;
        }

        // if the query is a decorated statement but the template is not, this is fine, and we pass through the observation

        if (query instanceof Stmt.Decorated decorated && !(template instanceof Stmt.Decorated)) {
            this.match(template, decorated.statement);
            return;
        }

        // we are ready to compare the two nodes
        // set the current query node and visit the template node

        this.currentQueryNode = query;

        switch (template) {
            case Stmt node -> node.accept(this);
            case Expr node -> node.accept(this);
            case AstType node -> node.accept(this);
            default -> throw new RuntimeException("Unknown type of AST node encountered. This should not happen.");
        }
    }

    /**
     * Transparently passes through nodes that are semantically equivalent to their inner expression:
     * groupings, variables that resolve to a statement, and assignment statements.
     * This allows the matcher to compare the underlying values rather than the syntactic wrappers.
     */
    private AstNode potentiallyPassThrough(AstNode node, VariableResolver variableResolver) {
        // we pass through groupings

        if (node instanceof Expr.Grouping grouping) {
            return this.potentiallyPassThrough(grouping.expression, variableResolver);
        }

        // we pass through variables directly into statements

        if (node instanceof Expr.Variable variable) {
            Stmt declarationStatement = variableResolver.resolveVariable(variable);

            if (declarationStatement != null) {
                // we pass through the variable into the statement
                return this.potentiallyPassThrough(declarationStatement, variableResolver);
            }
        }

        // we pass through assignment statements

        if (node instanceof Stmt.Assignment assignment) {
            return this.potentiallyPassThrough(assignment.expression, variableResolver);
        }

        // we don't pass through draws, observations, or decorated statements, because they have semantic meaning

        return node;
    }

    private void matchBlock(Stmt queryStmt) {
        // we treat no block, the data block, and the model block as exchangeable

        Set<Stmt.Block> exchangeableBlocks = Set.of(Stmt.Block.NO_BLOCK, Stmt.Block.DATA, Stmt.Block.MODEL);
        this.check(exchangeableBlocks.contains(this.templateBlock) == exchangeableBlocks.contains(queryStmt.block));

        if (!exchangeableBlocks.contains(this.templateBlock)) {
            // both of the statements are in a special block, let's ensure they are the same
            this.check(this.templateBlock == queryStmt.block);
        }
    }

    @Override
    public Void visitDecoratedStmt(Stmt.Decorated stmt) {
        if (!(this.currentQueryNode instanceof Stmt.Decorated queryStmt)) {
            throw new MatchingError();
        }

        this.match(stmt.decorator, queryStmt.decorator);
        this.match(stmt.statement, queryStmt.statement);

        return null;
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        if (!(this.currentQueryNode instanceof Stmt.Assignment queryStmt)) {
            throw new MatchingError();
        }

        // we can ignore the variable name, as we only care about the expression

        this.match(stmt.type, queryStmt.type);
        this.match(stmt.expression, queryStmt.expression);

        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        if (this.currentQueryNode instanceof Expr.DrawnArgument drawnArgumentQuery) {
            // in this case, the query is a drawn argument (x~dist) but the template is a drawn argument (Any x ~ dist)
            // this still works, we directly route to the drawn distribution

            this.match(stmt.expression, drawnArgumentQuery.expression);

            return null;
        }

        if (!(this.currentQueryNode instanceof Stmt.Draw queryStmt)) {
            throw new MatchingError();
        }

        // we can ignore the variable name, as we only care about the expression

        this.match(stmt.type, queryStmt.type);
        this.match(stmt.expression, queryStmt.expression);

        return null;
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        // import statements cannot be matched
        throw new MatchingError();
    }

    @Override
    public Void visitIndexedStmt(Stmt.Indexed indexed) {
        if (!(this.currentQueryNode instanceof Stmt.Indexed queryIndexed)) {
            throw new MatchingError();
        }

        this.check(indexed.indices.size() == queryIndexed.indices.size());
        this.check(indexed.ranges.size() == queryIndexed.ranges.size());

        // we bind the query index variables to the template index variables

        if (this.currentIndexBindings != null) {
            throw new RuntimeException("Non-null index bindings found. This should not happen.");
        }
        this.currentIndexBindings = new HashMap<>();

        for (int i = 0; i < indexed.indices.size(); i++) {
            String templateIndex = indexed.indices.get(i).variableName;
            String queryIndex = queryIndexed.indices.get(i).variableName;
            this.currentIndexBindings.put(queryIndex, templateIndex);
        }

        // match the ranges

        for (int i = 0; i < indexed.ranges.size(); i++) {
            this.match(indexed.ranges.get(i), queryIndexed.ranges.get(i));
        }

        // match the statement

        this.match(indexed.statement, queryIndexed.statement);

        // reset the index bindings

        this.currentIndexBindings = null;

        return null;
    }

    @Override
    public Void visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        if (!(this.currentQueryNode instanceof Stmt.ObservedAs queryObservedAs)) {
            throw new MatchingError();
        }

        this.match(observedAs.stmt, queryObservedAs.stmt);
        this.match(observedAs.observedAs, queryObservedAs.observedAs);

        return null;
    }

    @Override
    public Void visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        if (!(this.currentQueryNode instanceof Stmt.ObservedBetween queryObservedBetween)) {
            throw new MatchingError();
        }

        this.match(observedBetween.stmt, queryObservedBetween.stmt);
        this.match(observedBetween.observedFrom, queryObservedBetween.observedFrom);
        this.match(observedBetween.observedTo, queryObservedBetween.observedTo);

        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        if (!(this.currentQueryNode instanceof Expr.Literal queryLiteral)) {
            throw new MatchingError();
        }

        if (expr.value instanceof Number na && queryLiteral.value instanceof Number nb) {
            // this is a number and could be an int or a double. we treat 1 == 1.0
            this.check(Double.compare(na.doubleValue(), nb.doubleValue()) == 0);
        } else {
            this.check(expr.value.equals(queryLiteral.value));
        }


        if (expr.unit != Unit.IMPLICIT) {
            throw new RuntimeException("Template has explicit units, which is not supported.");
        }

        return null;
    }

    @Override
    public Void visitStringTemplate(Expr.StringTemplate expr) {
        throw new RuntimeException("String templates are not supported in PhyloSpec templates.");
    }

    @Override
    public Void visitVariable(Expr.Variable expr) {
        if (!(this.currentQueryNode instanceof Expr.Variable queryVariable)) {
            throw new MatchingError();
        }

        // this is a scoped variable, as we would have passed through global variables

        String queryVariableName = queryVariable.variableName;
        String boundVariableName = this.currentIndexBindings.get(queryVariableName);

        this.check(expr.variableName.equals(boundVariableName));

        return null;
    }

    @Override
    public Void visitTemplateVariable(Expr.TemplateVariable expr) {
        // template variables match any expression and capture the query node

        // if we have already encountered this template variable, it must have been resolved to the same AST node

        AstNode existingResolvedNode = templateVariableMap.get(expr.variableName);
        if (existingResolvedNode != null) {
            // the template variable was already bound; the new node must refer to the same thing.
            // two variable nodes are considered the same if they have the same name, even if they are different objects.

            boolean sameNode = existingResolvedNode == currentQueryNode;
            boolean sameVariable = existingResolvedNode instanceof Expr.Variable var1
                    && currentQueryNode instanceof Expr.Variable var2
                    && var1.variableName.equals(var2.variableName);

            if (!sameNode && !sameVariable) {
                throw new MatchingError();
            }
        }

        templateVariableMap.put(expr.variableName, currentQueryNode);

        return null;
    }

    @Override
    public Void visitOptionalTemplateVariable(Expr.OptionalTemplateVariable expr) {
        // optional template variables match any expression and capture the query node

        // if we have already encountered this template variable, it must have been resolved to the same AST node

        AstNode existingResolvedNode = templateVariableMap.get(expr.variableName);
        if (existingResolvedNode != null) {
            // the template variable was already bound; the new node must refer to the same thing.
            // two variable nodes are considered the same if they have the same name, even if they are different objects.

            boolean sameNode = existingResolvedNode == currentQueryNode;
            boolean sameVariable = existingResolvedNode instanceof Expr.Variable var1
                    && currentQueryNode instanceof Expr.Variable var2
                    && var1.variableName.equals(var2.variableName);

            if (!sameNode && !sameVariable) {
                throw new MatchingError();
            }
        }

        templateVariableMap.put(expr.variableName, currentQueryNode);

        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        if (!(this.currentQueryNode instanceof Expr.Unary queryUnary)) {
            throw new MatchingError();
        }

        this.check(expr.operator == queryUnary.operator);

        this.match(expr.right, queryUnary.right);

        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        if (!(this.currentQueryNode instanceof Expr.Binary queryBinary)) {
            throw new MatchingError();
        }

        this.check(expr.operator == queryBinary.operator);

        Map<String, AstNode> snapshot = new HashMap<>(this.templateVariableMap);
        try {
            this.match(expr.left, queryBinary.left);
            this.match(expr.right, queryBinary.right);
        } catch (MatchingError firstError) {
            // this did not work. restore bindings and try with swapped operand order

            this.templateVariableMap.clear();
            this.templateVariableMap.putAll(snapshot);

            try {
                this.match(expr.left, queryBinary.right);
                this.match(expr.right, queryBinary.left);
            } catch (MatchingError ignored) {
                // this also did not work
                // restore bindings and re-throw the error caused by the original order
                this.templateVariableMap.clear();
                this.templateVariableMap.putAll(snapshot);
                throw firstError;
            }
        }

        return null;
    }

    @Override
    public Void visitCall(Expr.Call expr) {
        if (!(this.currentQueryNode instanceof Expr.Call queryCall)) {
            throw new MatchingError();
        }

        this.check(expr.functionName.equals(queryCall.functionName));
        this.check(queryCall.arguments.length <= expr.arguments.length);

        if (expr.arguments.length == 0 && queryCall.arguments.length == 0) return null;

        if (expr.arguments.length == 1 && queryCall.arguments.length == 1) {
            // we directly match the only required argument
            this.match(expr.arguments[0], queryCall.arguments[0]);
            return null;
        }

        // collect the template arguments

        Map<String, Expr.Argument> templateArguments = new HashMap<>();
        String firstArgumentName = expr.arguments[0].name;

        for (Expr.Argument templateArgument : expr.arguments) {
            if (templateArgument.name == null) {
                throw new IllegalArgumentException("Generator argument for '" + expr.functionName + "' with no explicit name in template found. Please specify the argument names in templates.");
            }

            templateArguments.put(templateArgument.name, templateArgument);
        }

        // we go through the query arguments and try to match them

        Set<String> matchedTemplateArgumentNames = new HashSet<>();
        for (Expr.Argument queryArgument : queryCall.arguments) {
            String queryArgumentName = queryArgument.name;

            if (queryArgumentName == null && queryArgument.expression instanceof Expr.Variable queryVariable) {
                queryArgumentName = queryVariable.variableName;
            }

            if (queryArgumentName == null && queryArgument == queryCall.arguments[0]) {
                queryArgumentName = firstArgumentName;
            }

            this.check(queryArgumentName != null);

            Expr.Argument templateArgument = templateArguments.get(queryArgumentName);
            this.check(templateArgument != null);
            this.match(templateArgument, queryArgument);

            matchedTemplateArgumentNames.add(templateArgument.name);
        }

        // make sure that we have matched all non-optional template arguments

        for (Expr.Argument templateArgument : templateArguments.values()) {
            if (matchedTemplateArgumentNames.contains(templateArgument.name)) continue;

            // we don't have this template argument
            // this is only fine if it corresponds to an optional template variable
            if (templateArgument.expression instanceof Expr.OptionalTemplateVariable) continue;

            // this is not fine
            this.check(false);
        }

        return null;
    }

    @Override
    public Void visitAssignedArgument(Expr.AssignedArgument expr) {
        if (this.currentQueryNode instanceof Expr.DrawnArgument drawnQueryArg) {
            // in this case, the template is an assigned argument (x=dist) but the query is a drawn argument (x~dist)
            // this still works if the template has a variable pointing to a drawn statement

            this.check(expr.name.equals(drawnQueryArg.name));

            if (!(expr.expression instanceof Expr.Variable templateVar)) {
                throw new MatchingError();
            }

            AstNode resolvedTemplateStmt = this.potentiallyPassThrough(templateVar, this.templateVariableResolver);
            if (resolvedTemplateStmt == null) {
                throw new MatchingError();
            }

            if (!(resolvedTemplateStmt instanceof Stmt.Draw templateDrawStmt)) {
                throw new MatchingError();
            }

            this.match(templateDrawStmt.expression, drawnQueryArg.expression);

            return null;
        }

        if (!(this.currentQueryNode instanceof Expr.AssignedArgument queryArg)) {
            throw new MatchingError();
        }

        if (expr.name == null) {
            // we expect arguments in templates to have a name
            throw new IllegalArgumentException("Generator argument with no explicit name in template found. Please specify the argument names in templates.");
        }

        // we don't have to check the argument name as visitCall takes care of that
        // just check the expression

        this.match(expr.expression, queryArg.expression);

        return null;
    }

    @Override
    public Void visitDrawnArgument(Expr.DrawnArgument expr) {
        if (this.currentQueryNode instanceof Expr.AssignedArgument assignedQueryArg) {
            // in this case, the query is an assigned argument (x=dist) but the template is a drawn argument (x~dist)
            // this still works if the query has `dist` defined as a draw

            if (!(assignedQueryArg.expression instanceof Expr.Variable queryVar)) {
                throw new MatchingError();
            }

            AstNode resolvedQueryStmt = this.potentiallyPassThrough(queryVar, this.queryVariableResolver);
            if (resolvedQueryStmt == null) {
                throw new MatchingError();
            }

            if (!(resolvedQueryStmt instanceof Stmt.Draw queryDrawStmt)) {
                throw new MatchingError();
            }

            this.match(expr.expression, queryDrawStmt.expression);

            return null;
        }

        if (!(this.currentQueryNode instanceof Expr.DrawnArgument queryArg)) {
            throw new MatchingError();
        }

        // we don't have to check the argument name as visitCall takes care of that
        // just check the expression

        this.match(expr.expression, queryArg.expression);

        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        // we pass through all groupings
        // we should never end up here
        throw new RuntimeException("Visit groupings when matching templates. This should not happen.");
    }

    @Override
    public Void visitArray(Expr.Array expr) {
        if (!(this.currentQueryNode instanceof Expr.Array queryArray)) {
            throw new MatchingError();
        }

        this.check(expr.elements.size() == queryArray.elements.size());

        for (int i = 0; i < expr.elements.size(); i++) {
            this.match(expr.elements.get(i), queryArray.elements.get(i));
        }

        return null;
    }

    @Override
    public Void visitIndex(Expr.Index expr) {
        if (!(this.currentQueryNode instanceof Expr.Index queryIndex)) {
            throw new MatchingError();
        }

        this.check(expr.indices.size() == queryIndex.indices.size());

        this.match(expr.object, queryIndex.object);

        for (int i = 0; i < expr.indices.size(); i++) {
            this.match(expr.indices.get(i), queryIndex.indices.get(i));
        }

        return null;
    }

    @Override
    public Void visitRange(Expr.Range range) {
        if (!(this.currentQueryNode instanceof Expr.Range queryRange)) {
            throw new MatchingError();
        }

        this.match(range.from, queryRange.from);
        this.match(range.to, queryRange.to);

        return null;
    }

    @Override
    public Void visitAtomicType(AstType.Atomic expr) {
        if (expr.name.equals("Any")) return null;

        if (!(this.currentQueryNode instanceof AstType.Atomic queryAtomic)) {
            throw new MatchingError();
        }

        this.check(expr.name.equals(queryAtomic.name));

        return null;
    }

    @Override
    public Void visitGenericType(AstType.Generic expr) {
        if (expr.name.equals("Any")) return null;

        if (!(this.currentQueryNode instanceof AstType.Generic queryGeneric)) {
            throw new MatchingError();
        }

        this.check(expr.name.equals(queryGeneric.name));
        this.check(expr.typeParameters.length == queryGeneric.typeParameters.length);

        for (int i = 0; i < expr.typeParameters.length; i++) {
            this.match(expr.typeParameters[i], queryGeneric.typeParameters[i]);
        }

        return null;
    }

    /**
     * Checks the predicate and throws a MatchingError if it does not evaluate to true.
     */
    private void check(boolean predicate) {
        if (!predicate) throw new MatchingError();
    }

    private static class MatchingError extends RuntimeException {
    }
}
