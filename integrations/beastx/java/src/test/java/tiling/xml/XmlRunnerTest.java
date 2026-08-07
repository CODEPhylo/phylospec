package tiling.xml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlRunnerTest {

    @Test
    void priorOnlyXmlDoesNotRequireBeagle() {
        assertFalse(XmlRunner.requiresNativeBeagle("<beast><mcmc/></beast>"));
    }

    @Test
    void treeLikelihoodXmlRequiresBeagle() {
        assertTrue(XmlRunner.requiresNativeBeagle(
                "<beast><treeLikelihood id=\"likelihood\"/></beast>"
        ));
    }
}
