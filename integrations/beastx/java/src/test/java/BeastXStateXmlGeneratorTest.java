import org.junit.jupiter.api.Test;
import tiling.BeastXState;
import tiling.xml.BeastXStateXmlGenerator;
import tiling.xml.BeastXXmlElement;
import tiling.xml.BeastXXmlPlan;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXStateXmlGeneratorTest {

    @Test
    public void writesBeastGeneratorStyleSectionsFromXmlPlan() {
        BeastXState state =
                new BeastXState("xmlPlan");

        state.chainLength = 5;

        state.xmlPlan.add(
                BeastXXmlPlan.Section.TAXA,
                BeastXXmlElement.element("taxa")
                        .withId("taxa")
                        .withChild(BeastXXmlElement.element("taxon").withId("A"))
                        .withChild(BeastXXmlElement.element("taxon").withId("B"))
        );

        state.xmlPlan.add(
                BeastXXmlPlan.Section.MCMC_PRIOR,
                BeastXXmlElement.ref("distributionLikelihood", "x_prior")
        );

        state.xmlPlan.add(
                BeastXXmlPlan.Section.OPERATORS,
                BeastXXmlElement.element("scaleOperator")
                        .withId("x_scale")
                        .withAttribute("scaleFactor", "0.75")
                        .withAttribute("weight", "1.0")
                        .withChild(BeastXXmlElement.ref("parameter", "x"))
        );

        state.xmlPlan.add(
                BeastXXmlPlan.Section.MCMC_LOGGERS,
                BeastXXmlElement.element("log")
                        .withId("screenLogger")
                        .withAttribute("logEvery", "1")
                        .withChild(BeastXXmlElement.ref("parameter", "x"))
        );

        String xml =
                new BeastXStateXmlGenerator()
                        .toXml(state);

        assertTrue(xml.contains("<beast version=\"10.5.0\">"), xml);
        assertTrue(xml.contains("<taxa id=\"taxa\">"), xml);
        assertTrue(xml.contains("<operators id=\"operators\">"), xml);
        assertTrue(xml.contains("<mcmc id=\"xmlPlan_mcmc\" chainLength=\"5\""), xml);
        assertTrue(xml.contains("<prior id=\"prior\">"), xml);
        assertTrue(xml.contains("<distributionLikelihood idref=\"x_prior\"/>"), xml);
        assertTrue(xml.contains("<operators idref=\"operators\"/>"), xml);
        assertTrue(xml.indexOf("<operators id=\"operators\">") < xml.indexOf("<mcmc id=\"xmlPlan_mcmc\""), xml);
    }
}
