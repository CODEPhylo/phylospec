import beast.base.inference.*;
import beast.base.minimal.BeastMain;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.apache.commons.math4.legacy.analysis.function.Exp;
import org.phylospec.parser.Parser;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        Parser parser = new Parser(List.of());

        // we try to set up a BEAST run fully programatically

        MCMC mcmc = new MCMC();
        mcmc.determindClassOfInputs();

        mcmc.chainLengthInput.setValue((long) 10_000, null);

        // state

        State state = new State();
        state.determindClassOfInputs();

        List<StateNode> stateNodes = new ArrayList<>();

        RealScalarParam<Real> mean = new RealScalarParam<>();
        mean.set(2.0);
        stateNodes.add(mean);

        state.stateNodeInput.setValue(stateNodes, null);
        mcmc.startStateInput.setValue(state, null);

        // posterior

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.determindClassOfInputs();

        List<Distribution> distributions = new ArrayList<>();

        RealScalarParam<Real> pMean = new RealScalarParam<>();
        pMean.set(0.0);
        RealScalarParam<PositiveReal> pSd = new RealScalarParam<>();
        pSd.set(2.0);
        Normal meanPrior = new Normal(mean, pMean, pSd);
        distributions.add(meanPrior);

        RealScalarParam<Real> data = new RealScalarParam<>();
        data.set(5.0);
        RealScalarParam<PositiveReal> sd = new RealScalarParam<>();
        sd.set(1.0);
        Normal likelihood = new Normal(data, mean, sd);
        distributions.add(likelihood);

        posterior.pDistributions.setValue(distributions, null);
        mcmc.posteriorInput.setValue(posterior, null);

        // operators

        List<Operator> operators = new ArrayList<>();

        ScaleOperator meanScaleOperator = new ScaleOperator();
        meanScaleOperator.determindClassOfInputs();
        meanScaleOperator.parameterInput.setValue(mean, null);
        meanScaleOperator.m_pWeight.setValue(1.0, null);
        meanScaleOperator.initAndValidate();
        operators.add(meanScaleOperator);

        mcmc.operatorsInput.set(operators);

        mcmc.initAndValidate();

        try {
            mcmc.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Done");
    }
}
