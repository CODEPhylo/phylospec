package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.components.Argument__1;
import org.phylospec.components.Generator__1;
import org.phylospec.typeresolver.Stochasticity;

public class TemplateTileGeneratorPatternTest {

    @Test
    public void exposesOnePatternPerTemplateWithRootCall() {
        List<Generator__1> patterns = new CallTile().getGeneratorPatterns();

        assertEquals(2, patterns.size());

        Generator__1 first = patterns.getFirst();
        assertEquals("Normal", first.getName());
        assertNull(first.getNamespace());
        assertEquals(List.of(argument("mean", true, false), argument("sd", false, true)), first.getArguments());

        // the second template has an argument without a matching input
        Generator__1 second = patterns.get(1);
        assertEquals("Normal", second.getName());
        assertNull(second.getNamespace());
        assertEquals(List.of(argument("mean", true, true)), second.getArguments());
    }

    @Test
    public void exposesPatternsForAllCallsInAllStatements() {
        List<Generator__1> patterns = new MultiStatementTile().getGeneratorPatterns();

        assertEquals(
                List.of("Ploidy", "Coverage", "Model", "Exponential"),
                patterns.stream().map(Generator__1::getName).toList());

        // arguments which are not template variables are listed too
        assertEquals(
                List.of(argument("ploidy", true, true), argument("mean", true, true)),
                patterns.get(1).getArguments());
        assertEquals(List.of(argument("rate", true, true)), patterns.get(3).getArguments());
    }

    @Test
    public void exposesNoPatternWithoutRootCall() {
        assertTrue(new NoCallTile().getGeneratorPatterns().isEmpty());
    }

    @Test
    public void exposesNoPatternForMcmcTemplates() {
        assertTrue(new McmcTile().getGeneratorPatterns().isEmpty());
    }

    @Test
    public void exposesNoPatternForErrorTiles() {
        assertTrue(new RejectingTile().getGeneratorPatterns().isEmpty());
    }

    private static Argument__1 argument(String name, boolean required, boolean canBeStochastic) {
        Argument__1 argument = new Argument__1();
        argument.setName(name);
        argument.setRequired(required);
        argument.setCanBeStochastic(canBeStochastic);
        return argument;
    }

    static class CallTile extends TemplateTile<Object, Object> {
        @Override
        protected String getPhyloSpecTemplate() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected List<String> getPhyloSpecTemplates() {
            return List.of("Real x ~ Normal(mean=$mean, sd=$$sd)", "Real x ~ Normal(mean=$mean2)");
        }

        TemplateTileInput<Object, Object> meanInput = new TemplateTileInput<>("$mean", Set.of(Stochasticity.CONSTANT));

        TemplateTileInput<Object, Object> sdInput = new TemplateTileInput<>("$$sd", false);

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return null;
        }
    }

    static class MultiStatementTile extends TemplateTile<Object, Object> {
        @Override
        protected String getPhyloSpecTemplate() {
            return """
                    Matrix<PositiveInteger> ploidy ~ Ploidy(numCells=$numCells)
                    Matrix<NonNegativeInteger> coverage ~ Coverage(ploidy=ploidy, mean=$mean)
                    Model(coverage=coverage, prior=Exponential(rate=$rate))
                    """;
        }

        TemplateTileInput<Object, Object> numCellsInput = new TemplateTileInput<>("$numCells");
        TemplateTileInput<Object, Object> meanInput = new TemplateTileInput<>("$mean");
        TemplateTileInput<Object, Object> rateInput = new TemplateTileInput<>("$rate");

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return null;
        }
    }

    static class NoCallTile extends TemplateTile<Object, Object> {
        @Override
        protected String getPhyloSpecTemplate() {
            return "Any x ~ $distribution";
        }

        TemplateTileInput<Object, Object> distributionInput = new TemplateTileInput<>("$distribution");

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return null;
        }
    }

    static class McmcTile extends TemplateTile<Object, Object> {
        @Override
        protected String getPhyloSpecTemplate() {
            return """
                    mcmc {
                        Logger screenLogger = screenLogger(logEvery=$logEvery)
                    }""";
        }

        TemplateTileInput<Object, Object> logEveryInput = new TemplateTileInput<>("$logEvery");

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return null;
        }
    }

    static class RejectingTile extends ErrorTile<Object, Object> {
        @Override
        protected String getPhyloSpecTemplate() {
            return "Real x ~ Normal(mean=$mean)";
        }

        TemplateTileInput<Object, Object> meanInput = new TemplateTileInput<>("$mean");

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return null;
        }
    }
}
