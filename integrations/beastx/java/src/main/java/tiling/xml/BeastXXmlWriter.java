package tiling.xml;

import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciationModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.math.distributions.Distribution;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeastXXmlWriter {

    public void write(BeastXModel model, Path path) throws IOException {
        Path parent =
                path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, toXml(model), StandardCharsets.UTF_8);
    }

    public String toXml(BeastXModel model) {
        validateSupportedModel(model);

        StringBuilder xml =
                new StringBuilder();

        xml.append("<?xml version=\"1.0\" standalone=\"yes\"?>\n");
        xml.append("<beast version=\"10.5.0\">\n");

        appendBeastLevelDefinitions(xml, model.beastState);

        xml.append("    <mcmc id=\"")
                .append(escape(model.beastState.runName))
                .append("_mcmc\" chainLength=\"")
                .append(model.beastState.chainLength)
                .append("\">\n");

        appendPosterior(xml, model);
        appendOperators(xml, model.beastState);
        appendLoggers(xml, model);

        xml.append("    </mcmc>\n");
        xml.append("</beast>\n");

        return xml.toString();
    }

    private void validateSupportedModel(BeastXModel model) {
        BeastXState state =
                model.beastState;

        if (!state.calibrationPriorDistributions.isEmpty()) {
            throw unsupported("Calibration priors are not supported by this BEAST X XML writer yet.");
        }

        if (!state.likelihoodDistributions.isEmpty()) {
            throw unsupported("PhyloCTMC likelihood XML export is not supported yet.");
        }

        if (state.priorDistributions.isEmpty() && state.treePriorDistributions.isEmpty()) {
            throw unsupported("At least one scalar prior or tree prior is required.");
        }

        if (
                state.screenLoggerSpecs.isEmpty()
                        && state.fileLoggerSpecs.isEmpty()
                        && state.treeLoggerSpecs.isEmpty()
        ) {
            throw unsupported("At least one logger is required for XML MCMC execution.");
        }

        validateScalarPriors(state);
        validateTreePriors(state);
    }

    private void validateScalarPriors(BeastXState state) {
        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : state.priorDistributions.entrySet()) {
            Parameter parameter =
                    entry.getKey();

            AbstractDistributionLikelihood likelihood =
                    entry.getValue();

            if (parameter.getDimension() != 1) {
                throw unsupported("Only scalar parameters are supported by this BEAST X XML writer.");
            }

            if (!(likelihood instanceof DistributionLikelihood distributionLikelihood)) {
                throw unsupported("Only DistributionLikelihood priors are supported.");
            }

            Distribution distribution =
                    distributionLikelihood.getDistribution();

            if (!(distribution instanceof LogNormalDistributionModel)) {
                throw unsupported("Only LogNormal scalar priors are supported by this BEAST X XML writer.");
            }
        }
    }

    private void validateTreePriors(BeastXState state) {
        for (AbstractModelLikelihood treePrior : state.treePriorDistributions.values()) {
            SpeciationLikelihood speciationLikelihood =
                    asSpeciationLikelihood(treePrior);

            SpeciationModel speciationModel =
                    speciationLikelihood.getSpeciationModel();

            if (!(speciationModel instanceof BirthDeathGernhard08Model)) {
                throw unsupported("Only Yule and BirthDeath tree priors are supported by this BEAST X XML writer.");
            }
        }
    }

    private void appendBeastLevelDefinitions(StringBuilder xml, BeastXState state) {
        appendBeastLevelStateParameterDefinitions(xml, state);

        List<Map.Entry<TreeModel, AbstractModelLikelihood>> treeEntries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        treeEntries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        Set<String> emittedTaxonIds =
                new HashSet<>();

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : treeEntries) {
            appendTaxonDefinitions(xml, entry.getKey(), emittedTaxonIds);
            appendStartingTreeDefinition(xml, entry.getKey());
            appendTreeModelDefinition(xml, entry.getKey());
            appendTreePriorModelDefinition(xml, state, entry.getValue());
        }
    }

    private void appendBeastLevelStateParameterDefinitions(StringBuilder xml, BeastXState state) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(BeastXXmlWriter::parameterId));

        for (Parameter parameter : parameters) {
            appendParameterDefinition(xml, parameter, 4);
        }
    }

    private void appendTaxonDefinitions(
            StringBuilder xml,
            TreeModel treeModel,
            Set<String> emittedTaxonIds
    ) {
        for (int i = 0; i < treeModel.getTaxonCount(); i++) {
            String taxonId =
                    treeModel.getTaxonId(i);

            if (taxonId == null || taxonId.isBlank()) {
                throw new IllegalArgumentException("Cannot serialize unnamed BEAST X taxon.");
            }

            if (!emittedTaxonIds.add(taxonId)) {
                continue;
            }

            xml.append("    <taxon id=\"")
                    .append(escape(taxonId))
                    .append("\"/>\n");
        }
    }

    private void appendStartingTreeDefinition(StringBuilder xml, TreeModel treeModel) {
        xml.append("    <newick id=\"")
                .append(escape(startingTreeId(treeModel)))
                .append("\" units=\"years\" usingDates=\"false\" usingHeights=\"false\">")
                .append(escape(ensureTrailingSemicolon(treeModel.getNewick())))
                .append("</newick>\n");
    }

    private void appendTreeModelDefinition(StringBuilder xml, TreeModel treeModel) {
        String id =
                treeId(treeModel);

        xml.append("    <treeModel id=\"")
                .append(escape(id))
                .append("\">\n");

        xml.append("        <newick idref=\"")
                .append(escape(startingTreeId(treeModel)))
                .append("\"/>\n");

        xml.append("        <rootHeight>\n");
        xml.append("            <parameter id=\"")
                .append(escape(id))
                .append(".rootHeight\"/>\n");
        xml.append("        </rootHeight>\n");

        xml.append("        <nodeHeights internalNodes=\"true\" rootNode=\"false\">\n");
        xml.append("            <parameter id=\"")
                .append(escape(id))
                .append(".internalNodeHeights\"/>\n");
        xml.append("        </nodeHeights>\n");

        xml.append("        <nodeHeights internalNodes=\"true\" rootNode=\"true\">\n");
        xml.append("            <parameter id=\"")
                .append(escape(id))
                .append(".allInternalNodeHeights\"/>\n");
        xml.append("        </nodeHeights>\n");

        xml.append("    </treeModel>\n");
    }

    private void appendTreePriorModelDefinition(
            StringBuilder xml,
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        BirthDeathGernhard08Model birthDeathModel =
                getBirthDeathModel(treePrior);

        if (isYuleCompatible(birthDeathModel)) {
            appendYuleModelDefinition(xml, state, treePrior, birthDeathModel);
        } else {
            appendBirthDeathModelDefinition(xml, state, treePrior, birthDeathModel);
        }
    }

    private void appendYuleModelDefinition(
            StringBuilder xml,
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model yuleModel
    ) {
        xml.append("    <yuleModel id=\"")
                .append(escape(treePriorModelId(treePrior)))
                .append("\" units=\"years\">\n");

        appendBirthDeathParameterElement(
                xml,
                state,
                "birthRate",
                birthDeathVariable(yuleModel, 0),
                priorId(treePrior) + "_birthRate",
                yuleModel.getR(),
                0.0,
                null
        );

        xml.append("    </yuleModel>\n");
    }

    private void appendBirthDeathModelDefinition(
            StringBuilder xml,
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model birthDeathModel
    ) {
        xml.append("    <birthDeathModel id=\"")
                .append(escape(treePriorModelId(treePrior)))
                .append("\" type=\"LABELED\" units=\"years\">\n");

        appendBirthDeathParameterElement(
                xml,
                state,
                "birthMinusDeathRate",
                birthDeathVariable(birthDeathModel, 0),
                priorId(treePrior) + "_birthMinusDeathRate",
                birthDeathModel.getR(),
                0.0,
                null
        );

        appendBirthDeathParameterElement(
                xml,
                state,
                "relativeDeathRate",
                birthDeathVariable(birthDeathModel, 1),
                priorId(treePrior) + "_relativeDeathRate",
                birthDeathModel.getA(),
                0.0,
                1.0
        );

        appendBirthDeathParameterElement(
                xml,
                state,
                "sampleProbability",
                birthDeathVariable(birthDeathModel, 2),
                priorId(treePrior) + "_sampleProbability",
                birthDeathModel.getRho(),
                0.0,
                1.0
        );

        xml.append("    </birthDeathModel>\n");
    }

    private void appendBirthDeathParameterElement(
            StringBuilder xml,
            BeastXState state,
            String elementName,
            Parameter parameter,
            String fallbackId,
            double fallbackValue,
            Double lower,
            Double upper
    ) {
        xml.append("        <")
                .append(elementName)
                .append(">\n");

        if (parameter != null && state.stateNodes.containsKey(parameter)) {
            appendParameterReference(xml, parameter, 12);
        } else {
            appendInlineParameterDefinition(xml, fallbackId, fallbackValue, lower, upper, 12);
        }

        xml.append("        </")
                .append(elementName)
                .append(">\n");
    }

    private void appendPosterior(StringBuilder xml, BeastXModel model) {
        xml.append("        <posterior id=\"posterior\">\n");
        xml.append("            <prior id=\"prior\">\n");

        appendScalarPriors(xml, model.beastState);
        appendTreePriors(xml, model.beastState);

        xml.append("            </prior>\n");
        xml.append("        </posterior>\n");
    }

    private void appendScalarPriors(StringBuilder xml, BeastXState state) {
        List<Map.Entry<Parameter, AbstractDistributionLikelihood>> entries =
                new ArrayList<>(state.priorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : entries) {
            appendLogNormalPrior(xml, entry.getKey(), (DistributionLikelihood) entry.getValue());
        }
    }

    private void appendTreePriors(StringBuilder xml, BeastXState state) {
        List<Map.Entry<TreeModel, AbstractModelLikelihood>> entries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : entries) {
            appendTreePrior(xml, entry.getKey(), entry.getValue());
        }
    }

    private void appendLogNormalPrior(
            StringBuilder xml,
            Parameter parameter,
            DistributionLikelihood likelihood
    ) {
        LogNormalDistributionModel distribution =
                (LogNormalDistributionModel) likelihood.getDistribution();

        String priorId =
                likelihood.getId();

        xml.append("                <distributionLikelihood id=\"")
                .append(escape(priorId))
                .append("\">\n");

        xml.append("                    <distribution>\n");
        xml.append("                        <logNormalDistributionModel id=\"")
                .append(escape(priorId))
                .append("_distribution\">\n");

        xml.append("                            <mu>\n");
        xml.append("                                <parameter id=\"")
                .append(escape(priorId))
                .append("_mu\" value=\"")
                .append(format(distribution.getMu()))
                .append("\"/>\n");
        xml.append("                            </mu>\n");

        xml.append("                            <precision>\n");
        xml.append("                                <parameter id=\"")
                .append(escape(priorId))
                .append("_precision\" value=\"")
                .append(format(distribution.getPrecision()))
                .append("\"/>\n");
        xml.append("                            </precision>\n");

        xml.append("                        </logNormalDistributionModel>\n");
        xml.append("                    </distribution>\n");

        xml.append("                    <data>\n");
        appendParameterReference(xml, parameter, 24);
        xml.append("                    </data>\n");

        xml.append("                </distributionLikelihood>\n");
    }

    private void appendTreePrior(
            StringBuilder xml,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        BirthDeathGernhard08Model birthDeathModel =
                getBirthDeathModel(treePrior);

        String modelElement =
                isYuleCompatible(birthDeathModel)
                        ? "yuleModel"
                        : "birthDeathModel";

        xml.append("                <speciationLikelihood id=\"")
                .append(escape(priorId(treePrior)))
                .append("\">\n");

        xml.append("                    <model>\n");
        xml.append("                        <")
                .append(modelElement)
                .append(" idref=\"")
                .append(escape(treePriorModelId(treePrior)))
                .append("\"/>\n");
        xml.append("                    </model>\n");

        xml.append("                    <speciesTree>\n");
        appendTreeReference(xml, treeModel, 24);
        xml.append("                    </speciesTree>\n");

        xml.append("                </speciationLikelihood>\n");
    }

    private void appendOperators(StringBuilder xml, BeastXState state) {
        xml.append("        <operators>\n");

        appendParameterOperators(xml, state);
        appendTreeOperators(xml, state);

        xml.append("        </operators>\n");
    }

    private void appendParameterOperators(StringBuilder xml, BeastXState state) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(BeastXXmlWriter::parameterId));

        for (Parameter parameter : parameters) {
            if (hasFiniteLowerAndUpperBounds(parameter)) {
                appendRandomWalkOperator(xml, state, parameter);
            } else {
                appendScaleOperator(xml, state, parameter);
            }
        }
    }

    private void appendScaleOperator(
            StringBuilder xml,
            BeastXState state,
            Parameter parameter
    ) {
        String parameterId =
                parameterId(parameter);

        xml.append("            <scaleOperator id=\"")
                .append(escape(parameterId))
                .append("_scale\" scaleFactor=\"")
                .append(format(state.operatorConfig.parameterScaleFactor))
                .append("\" weight=\"")
                .append(format(state.operatorConfig.parameterOperatorWeight))
                .append("\">\n");

        appendParameterReference(xml, parameter, 16);

        xml.append("            </scaleOperator>\n");
    }

    private void appendRandomWalkOperator(
            StringBuilder xml,
            BeastXState state,
            Parameter parameter
    ) {
        String parameterId =
                parameterId(parameter);

        xml.append("            <randomWalkOperator id=\"")
                .append(escape(parameterId))
                .append("_randomWalk\" windowSize=\"")
                .append(format(state.operatorConfig.randomWalkWindowSize))
                .append("\" weight=\"")
                .append(format(state.operatorConfig.parameterOperatorWeight))
                .append("\" boundaryCondition=\"reflecting\">\n");

        appendParameterReference(xml, parameter, 16);

        xml.append("            </randomWalkOperator>\n");
    }

    private static boolean hasFiniteLowerAndUpperBounds(Parameter parameter) {
        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds == null) {
            return false;
        }

        double lower =
                bounds.getLowerLimit(0);

        double upper =
                bounds.getUpperLimit(0);

        return Double.isFinite(lower) && Double.isFinite(upper);
    }

    private void appendTreeOperators(StringBuilder xml, BeastXState state) {
        List<TreeModel> trees =
                new ArrayList<>(state.treePriorDistributions.keySet());

        trees.sort(Comparator.comparing(BeastXXmlWriter::treeId));

        for (TreeModel treeModel : trees) {
            String id =
                    treeId(treeModel);

            appendTreeOperator(
                    xml,
                    "narrowExchange",
                    id + "_narrowExchange",
                    state.operatorConfig.treeNarrowExchangeWeight,
                    treeModel
            );

            appendTreeOperator(
                    xml,
                    "wideExchange",
                    id + "_wideExchange",
                    state.operatorConfig.treeWideExchangeWeight,
                    treeModel
            );

            appendSubtreeSlideOperator(xml, state, treeModel);

            appendTreeOperator(
                    xml,
                    "wilsonBalding",
                    id + "_wilsonBalding",
                    state.operatorConfig.treeWilsonBaldingWeight,
                    treeModel
            );
        }
    }

    private void appendTreeOperator(
            StringBuilder xml,
            String elementName,
            String id,
            double weight,
            TreeModel treeModel
    ) {
        xml.append("            <")
                .append(elementName)
                .append(" id=\"")
                .append(escape(id))
                .append("\" weight=\"")
                .append(format(weight))
                .append("\">\n");

        appendTreeReference(xml, treeModel, 16);

        xml.append("            </")
                .append(elementName)
                .append(">\n");
    }

    private void appendSubtreeSlideOperator(
            StringBuilder xml,
            BeastXState state,
            TreeModel treeModel
    ) {
        String id =
                treeId(treeModel);

        xml.append("            <subtreeSlide id=\"")
                .append(escape(id))
                .append("_subtreeSlide\" weight=\"")
                .append(format(state.operatorConfig.treeSubtreeSlideWeight))
                .append("\" size=\"")
                .append(format(state.operatorConfig.treeSubtreeSlideSize))
                .append("\" gaussian=\"true\">\n");

        appendTreeReference(xml, treeModel, 16);

        xml.append("            </subtreeSlide>\n");
    }

    private void appendLoggers(StringBuilder xml, BeastXModel model) {
        BeastXState state =
                model.beastState;

        int loggerIndex =
                1;

        for (BeastXState.ScreenLoggerSpec spec : state.screenLoggerSpecs) {
            appendParameterLogger(
                    xml,
                    "screenLogger" + loggerIndex,
                    spec.logEvery,
                    null,
                    getLoggedParameters(state, spec.parameterNames)
            );

            loggerIndex++;
        }

        for (BeastXState.FileLoggerSpec spec : state.fileLoggerSpecs) {
            appendParameterLogger(
                    xml,
                    "fileLogger" + loggerIndex,
                    spec.logEvery,
                    spec.fileName,
                    getLoggedParameters(state, spec.parameterNames)
            );

            loggerIndex++;
        }

        int treeLoggerIndex =
                1;

        for (BeastXState.TreeLoggerSpec spec : state.treeLoggerSpecs) {
            appendTreeLogger(
                    xml,
                    "treeLogger" + treeLoggerIndex,
                    spec.logEvery,
                    spec.fileName,
                    getLoggedTrees(state, spec.treeNames)
            );

            treeLoggerIndex++;
        }
    }

    private void appendParameterLogger(
            StringBuilder xml,
            String id,
            long logEvery,
            String fileName,
            List<Parameter> parameters
    ) {
        xml.append("        <log id=\"")
                .append(escape(id))
                .append("\" logEvery=\"")
                .append(logEvery)
                .append("\"");

        if (fileName != null) {
            xml.append(" fileName=\"")
                    .append(escape(fileName))
                    .append("\" overwrite=\"true\"");
        }

        xml.append(">\n");

        for (Parameter parameter : parameters) {
            appendParameterReference(xml, parameter, 12);
        }

        xml.append("        </log>\n");
    }

    private void appendTreeLogger(
            StringBuilder xml,
            String id,
            long logEvery,
            String fileName,
            List<TreeModel> treeModels
    ) {
        xml.append("        <logTree id=\"")
                .append(escape(id))
                .append("\" logEvery=\"")
                .append(logEvery)
                .append("\" fileName=\"")
                .append(escape(fileName))
                .append("\" overwrite=\"true\" nexusFormat=\"true\">\n");

        for (TreeModel treeModel : treeModels) {
            appendTreeReference(xml, treeModel, 12);
        }

        xml.append("        </logTree>\n");
    }

    private List<Parameter> getLoggedParameters(
            BeastXState state,
            List<String> parameterNames
    ) {
        List<Parameter> parameters =
                new ArrayList<>();

        if (parameterNames == null) {
            parameters.addAll(state.stateNodes.keySet());
        } else {
            for (String parameterName : parameterNames) {
                Parameter parameter =
                        state.stateNodesByPhyloSpecName.get(parameterName);

                if (parameter == null) {
                    throw new IllegalArgumentException(
                            "No BEAST X state node named '" + parameterName + "' exists for XML logger."
                    );
                }

                parameters.add(parameter);
            }
        }

        parameters.sort(Comparator.comparing(BeastXXmlWriter::parameterId));

        return parameters;
    }

    private List<TreeModel> getLoggedTrees(
            BeastXState state,
            List<String> treeNames
    ) {
        List<TreeModel> trees =
                new ArrayList<>();

        if (treeNames == null) {
            trees.addAll(state.treePriorDistributions.keySet());
        } else {
            for (String treeName : treeNames) {
                TreeModel treeModel =
                        state.treeModelsByPhyloSpecName.get(treeName);

                if (treeModel == null) {
                    throw new IllegalArgumentException(
                            "No BEAST X tree model named '" + treeName + "' exists for XML tree logger."
                    );
                }

                trees.add(treeModel);
            }
        }

        trees.sort(Comparator.comparing(BeastXXmlWriter::treeId));

        return trees;
    }

    private static void appendParameterDefinition(
            StringBuilder xml,
            Parameter parameter,
            int indent
    ) {
        String padding =
                " ".repeat(indent);

        xml.append(padding)
                .append("<parameter id=\"")
                .append(escape(parameterId(parameter)))
                .append("\" value=\"")
                .append(format(parameter.getParameterValue(0)))
                .append("\"");

        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds != null) {
            double lower =
                    bounds.getLowerLimit(0);

            double upper =
                    bounds.getUpperLimit(0);

            if (Double.isFinite(lower)) {
                xml.append(" lower=\"")
                        .append(format(lower))
                        .append("\"");
            }

            if (Double.isFinite(upper)) {
                xml.append(" upper=\"")
                        .append(format(upper))
                        .append("\"");
            }
        }

        xml.append("/>\n");
    }

    private static void appendInlineParameterDefinition(
            StringBuilder xml,
            String id,
            double value,
            Double lower,
            Double upper,
            int indent
    ) {
        String padding =
                " ".repeat(indent);

        xml.append(padding)
                .append("<parameter id=\"")
                .append(escape(id))
                .append("\" value=\"")
                .append(format(value))
                .append("\"");

        if (lower != null) {
            xml.append(" lower=\"")
                    .append(format(lower))
                    .append("\"");
        }

        if (upper != null) {
            xml.append(" upper=\"")
                    .append(format(upper))
                    .append("\"");
        }

        xml.append("/>\n");
    }

    private static void appendParameterReference(
            StringBuilder xml,
            Parameter parameter,
            int indent
    ) {
        String padding =
                " ".repeat(indent);

        xml.append(padding)
                .append("<parameter idref=\"")
                .append(escape(parameterId(parameter)))
                .append("\"/>\n");
    }

    private static void appendTreeReference(
            StringBuilder xml,
            TreeModel treeModel,
            int indent
    ) {
        String padding =
                " ".repeat(indent);

        xml.append(padding)
                .append("<treeModel idref=\"")
                .append(escape(treeId(treeModel)))
                .append("\"/>\n");
    }

    private static SpeciationLikelihood asSpeciationLikelihood(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof SpeciationLikelihood speciationLikelihood)) {
            throw unsupported("Only SpeciationLikelihood tree priors are supported by this BEAST X XML writer.");
        }

        return speciationLikelihood;
    }

    private static BirthDeathGernhard08Model getBirthDeathModel(AbstractModelLikelihood treePrior) {
        SpeciationLikelihood speciationLikelihood =
                asSpeciationLikelihood(treePrior);

        SpeciationModel speciationModel =
                speciationLikelihood.getSpeciationModel();

        if (!(speciationModel instanceof BirthDeathGernhard08Model birthDeathModel)) {
            throw unsupported("Only Yule and BirthDeath tree priors are supported by this BEAST X XML writer.");
        }

        return birthDeathModel;
    }

    private static Parameter birthDeathVariable(
            BirthDeathGernhard08Model birthDeathModel,
            int variableIndex
    ) {
        if (variableIndex >= birthDeathModel.getVariableCount()) {
            return null;
        }

        Variable<?> variable =
                birthDeathModel.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        return null;
    }

    private static boolean isYuleCompatible(BirthDeathGernhard08Model model) {
        return model.isYule()
                || (
                approximatelyZero(model.getA())
                        && approximatelyOne(model.getRho())
        );
    }

    private static boolean approximatelyZero(double value) {
        return Math.abs(value) < 1.0e-12;
    }

    private static boolean approximatelyOne(double value) {
        return Math.abs(value - 1.0) < 1.0e-12;
    }

    private static String parameterId(Parameter parameter) {
        String id =
                parameter.getId();

        if (id == null || id.isBlank()) {
            id =
                    parameter.getParameterName();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X parameter.");
        }

        return id;
    }

    private static String treeId(TreeModel treeModel) {
        String id =
                treeModel.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X tree model.");
        }

        return id;
    }

    private static String priorId(AbstractModelLikelihood prior) {
        String id =
                prior.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X prior.");
        }

        return id;
    }

    private static String startingTreeId(TreeModel treeModel) {
        return treeId(treeModel) + "_startingTree";
    }

    private static String treePriorModelId(AbstractModelLikelihood treePrior) {
        return priorId(treePrior) + "_model";
    }

    private static String ensureTrailingSemicolon(String newick) {
        String trimmed =
                newick.trim();

        if (trimmed.endsWith(";")) {
            return trimmed;
        }

        return trimmed + ";";
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend BeastXXmlWriter before exporting this model class to XML."
        );
    }

    private static String format(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot serialize NaN as a BEAST X XML number.");
        }

        if (value == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }

        if (value == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }

        return Double.toString(value);
    }

    private static String escape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}