package tiling.xml;

import dr.app.beauti.util.XMLWriter;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciationModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.BetaDistributionModel;
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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeastXOfficialXmlWriter {

    public void write(
            BeastXModel model,
            Path path
    ) throws IOException {
        Path parent =
                path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(
                path,
                toXml(model),
                StandardCharsets.UTF_8
        );
    }

    public String toXml(BeastXModel model) {
        validateSupportedModel(model);

        StringWriter output =
                new StringWriter();

        XMLWriter writer =
                new XMLWriter(output);

        writer.writeText("<?xml version=\"1.0\" standalone=\"yes\"?>");
        writer.writeBlankLine();

        writer.writeOpenTag("beast version=\"10.5.0\"");

        writeBeastLevelDefinitions(writer, model.beastState);
        writeMCMC(writer, model);

        writer.writeCloseTag("beast");
        writer.flush();

        return output.toString();
    }

    private void validateSupportedModel(BeastXModel model) {
        BeastXState state =
                model.beastState;

        if (!state.likelihoodDistributions.isEmpty()) {
            throw unsupported("PhyloCTMC likelihood XML export is not supported by BeastXOfficialXmlWriter yet.");
        }

        if (!state.calibrationPriorDistributions.isEmpty()) {
            throw unsupported("Calibration prior XML export is not supported by BeastXOfficialXmlWriter yet.");
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
                throw unsupported("Only scalar parameters are supported.");
            }

            if (!(likelihood instanceof DistributionLikelihood distributionLikelihood)) {
                throw unsupported("Only DistributionLikelihood scalar priors are supported.");
            }

            Distribution distribution =
                    distributionLikelihood.getDistribution();

            if (
                    !(distribution instanceof LogNormalDistributionModel)
                            && !(distribution instanceof BetaDistributionModel)
            ) {
                throw unsupported("Only LogNormal and Beta scalar priors are supported.");
            }
        }
    }

    private void validateTreePriors(BeastXState state) {
        for (AbstractModelLikelihood treePrior : state.treePriorDistributions.values()) {
            if (treePrior instanceof SpeciationLikelihood speciationLikelihood) {
                SpeciationModel speciationModel =
                        speciationLikelihood.getSpeciationModel();

                if (!(speciationModel instanceof BirthDeathGernhard08Model)) {
                    throw unsupported("Only Yule and BirthDeath speciation tree priors are supported.");
                }
            } else if (treePrior instanceof CoalescentLikelihood coalescentLikelihood) {
                DemographicModel demographicModel =
                        coalescentLikelihood.getDemoModel();

                if (!(demographicModel instanceof ConstantPopulationModel)) {
                    throw unsupported("Only constant-population Coalescent tree priors are supported.");
                }
            } else {
                throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
            }
        }
    }

    private void writeBeastLevelDefinitions(
            XMLWriter writer,
            BeastXState state
    ) {
        writeStateParameters(writer, state);

        List<Map.Entry<TreeModel, AbstractModelLikelihood>> treeEntries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        treeEntries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        Set<String> emittedTaxonIds =
                new HashSet<>();

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : treeEntries) {
            writeTaxonDefinitions(writer, entry.getKey(), emittedTaxonIds);
            writeStartingTreeDefinition(writer, entry.getKey());
            writeTreeModelDefinition(writer, entry.getKey());
            writeTreePriorModelDefinition(writer, state, entry.getValue());
        }
    }

    private void writeStateParameters(
            XMLWriter writer,
            BeastXState state
    ) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(BeastXOfficialXmlWriter::parameterId));

        for (Parameter parameter : parameters) {
            writeParameterDefinition(writer, parameter);
        }
    }

    private void writeTaxonDefinitions(
            XMLWriter writer,
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

            writer.writeOpenTag(
                    "taxon id=\""
                            + escapeXmlAttribute(taxonId)
                            + "\""
            );
            writer.writeCloseTag("taxon");
        }
    }

    private void writeStartingTreeDefinition(
            XMLWriter writer,
            TreeModel treeModel
    ) {
        writer.writeOpenTag(
                "newick id=\""
                        + escapeXmlAttribute(startingTreeId(treeModel))
                        + "\" units=\"years\" usingDates=\"false\" usingHeights=\"false\""
        );

        writer.writeText(ensureTrailingSemicolon(treeModel.getNewick()));

        writer.writeCloseTag("newick");
    }

    private void writeTreeModelDefinition(
            XMLWriter writer,
            TreeModel treeModel
    ) {
        String id =
                treeId(treeModel);

        writer.writeOpenTag(
                "treeModel id=\""
                        + escapeXmlAttribute(id)
                        + "\""
        );

        writer.writeOpenTag(
                "newick idref=\""
                        + escapeXmlAttribute(startingTreeId(treeModel))
                        + "\""
        );
        writer.writeCloseTag("newick");

        writer.writeOpenTag("rootHeight");
        writer.writeOpenTag(
                "parameter id=\""
                        + escapeXmlAttribute(id)
                        + ".rootHeight\""
        );
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("rootHeight");

        writer.writeOpenTag("nodeHeights internalNodes=\"true\" rootNode=\"false\"");
        writer.writeOpenTag(
                "parameter id=\""
                        + escapeXmlAttribute(id)
                        + ".internalNodeHeights\""
        );
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("nodeHeights");

        writer.writeOpenTag("nodeHeights internalNodes=\"true\" rootNode=\"true\"");
        writer.writeOpenTag(
                "parameter id=\""
                        + escapeXmlAttribute(id)
                        + ".allInternalNodeHeights\""
        );
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("nodeHeights");

        writer.writeCloseTag("treeModel");
    }

    private void writeTreePriorModelDefinition(
            XMLWriter writer,
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        if (treePrior instanceof SpeciationLikelihood) {
            BirthDeathGernhard08Model birthDeathModel =
                    getBirthDeathModel(treePrior);

            if (isYuleCompatible(birthDeathModel)) {
                writeYuleModelDefinition(writer, state, treePrior, birthDeathModel);
            } else {
                writeBirthDeathModelDefinition(writer, state, treePrior, birthDeathModel);
            }

            return;
        }

        if (treePrior instanceof CoalescentLikelihood) {
            writeConstantPopulationModelDefinition(writer, state, treePrior);
            return;
        }

        throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
    }

    private void writeYuleModelDefinition(
            XMLWriter writer,
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model yuleModel
    ) {
        writer.writeOpenTag(
                "yuleModel id=\""
                        + escapeXmlAttribute(treePriorModelId(treePrior))
                        + "\" units=\"years\""
        );

        writeParameterElement(
                writer,
                state,
                "birthRate",
                birthDeathVariable(yuleModel, 0),
                priorId(treePrior) + "_birthRate",
                yuleModel.getR(),
                0.0,
                null
        );

        writer.writeCloseTag("yuleModel");
    }

    private void writeBirthDeathModelDefinition(
            XMLWriter writer,
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model birthDeathModel
    ) {
        writer.writeOpenTag(
                "birthDeathModel id=\""
                        + escapeXmlAttribute(treePriorModelId(treePrior))
                        + "\" type=\"LABELED\" units=\"years\""
        );

        writeParameterElement(
                writer,
                state,
                "birthMinusDeathRate",
                birthDeathVariable(birthDeathModel, 0),
                priorId(treePrior) + "_birthMinusDeathRate",
                birthDeathModel.getR(),
                0.0,
                null
        );

        writeParameterElement(
                writer,
                state,
                "relativeDeathRate",
                birthDeathVariable(birthDeathModel, 1),
                priorId(treePrior) + "_relativeDeathRate",
                birthDeathModel.getA(),
                0.0,
                1.0
        );

        writeParameterElement(
                writer,
                state,
                "sampleProbability",
                birthDeathVariable(birthDeathModel, 2),
                priorId(treePrior) + "_sampleProbability",
                birthDeathModel.getRho(),
                0.0,
                1.0
        );

        writer.writeCloseTag("birthDeathModel");
    }

    private void writeConstantPopulationModelDefinition(
            XMLWriter writer,
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        ConstantPopulationModel constantPopulationModel =
                getConstantPopulationModel(treePrior);

        Parameter populationSize =
                constantPopulationVariable(constantPopulationModel);

        writer.writeOpenTag(
                "constantSize id=\""
                        + escapeXmlAttribute(treePriorModelId(treePrior))
                        + "\" units=\"years\""
        );

        writeParameterElement(
                writer,
                state,
                "populationSize",
                populationSize,
                priorId(treePrior) + "_populationSize",
                populationSize.getParameterValue(0),
                0.0,
                null
        );

        writer.writeCloseTag("constantSize");
    }

    private void writeParameterElement(
            XMLWriter writer,
            BeastXState state,
            String elementName,
            Parameter parameter,
            String fallbackId,
            double fallbackValue,
            Double lower,
            Double upper
    ) {
        writer.writeOpenTag(elementName);

        if (parameter != null && state.stateNodes.containsKey(parameter)) {
            writeParameterReference(writer, parameter);
        } else {
            writeInlineParameterDefinition(writer, fallbackId, fallbackValue, lower, upper);
        }

        writer.writeCloseTag(elementName);
    }

    private void writeMCMC(
            XMLWriter writer,
            BeastXModel model
    ) {
        BeastXState state =
                model.beastState;

        writer.writeOpenTag(
                "mcmc id=\""
                        + escapeXmlAttribute(state.runName)
                        + "_mcmc\" chainLength=\""
                        + state.chainLength
                        + "\""
        );

        writePosterior(writer, state);
        writeOperators(writer, state);
        writeLoggers(writer, state);

        writer.writeCloseTag("mcmc");
    }

    private void writePosterior(
            XMLWriter writer,
            BeastXState state
    ) {
        writer.writeOpenTag("posterior id=\"posterior\"");
        writer.writeOpenTag("prior id=\"prior\"");

        writeScalarPriors(writer, state);
        writeTreePriors(writer, state);

        writer.writeCloseTag("prior");
        writer.writeCloseTag("posterior");
    }

    private void writeScalarPriors(
            XMLWriter writer,
            BeastXState state
    ) {
        List<Map.Entry<Parameter, AbstractDistributionLikelihood>> entries =
                new ArrayList<>(state.priorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : entries) {
            DistributionLikelihood likelihood =
                    (DistributionLikelihood) entry.getValue();

            Distribution distribution =
                    likelihood.getDistribution();

            if (distribution instanceof LogNormalDistributionModel logNormalDistribution) {
                writeLogNormalPrior(writer, entry.getKey(), likelihood, logNormalDistribution);
            } else if (distribution instanceof BetaDistributionModel betaDistribution) {
                writeBetaPrior(writer, entry.getKey(), likelihood, betaDistribution);
            } else {
                throw unsupported("Only LogNormal and Beta scalar priors are supported.");
            }
        }
    }

    private void writeLogNormalPrior(
            XMLWriter writer,
            Parameter parameter,
            DistributionLikelihood likelihood,
            LogNormalDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        writer.writeOpenTag(
                "distributionLikelihood id=\""
                        + escapeXmlAttribute(priorId)
                        + "\""
        );

        writer.writeOpenTag("distribution");

        writer.writeOpenTag(
                "logNormalDistributionModel id=\""
                        + escapeXmlAttribute(priorId)
                        + "_distribution\""
        );

        writer.writeOpenTag("mu");
        writer.writeOpenTag(
                "parameter id=\""
                        + escapeXmlAttribute(priorId)
                        + "_mu\" value=\""
                        + format(distribution.getMu())
                        + "\""
        );
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("mu");

        writer.writeOpenTag("precision");
        writer.writeOpenTag(
                "parameter id=\""
                        + escapeXmlAttribute(priorId)
                        + "_precision\" value=\""
                        + format(distribution.getPrecision())
                        + "\""
        );
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("precision");

        writer.writeCloseTag("logNormalDistributionModel");

        writer.writeCloseTag("distribution");

        writer.writeOpenTag("data");
        writeParameterReference(writer, parameter);
        writer.writeCloseTag("data");

        writer.writeCloseTag("distributionLikelihood");
    }

    private void writeBetaPrior(
            XMLWriter writer,
            Parameter parameter,
            DistributionLikelihood likelihood,
            BetaDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        writer.writeOpenTag(
                "distributionLikelihood id=\""
                        + escapeXmlAttribute(priorId)
                        + "\""
        );

        writer.writeOpenTag("distribution");

        writer.writeOpenTag(
                "betaDistributionModel id=\""
                        + escapeXmlAttribute(priorId)
                        + "_distribution\""
        );

        writeBetaShapeParameter(writer, "alpha", betaDistributionVariable(distribution, 0), priorId + "_alpha");
        writeBetaShapeParameter(writer, "beta", betaDistributionVariable(distribution, 1), priorId + "_beta");

        writer.writeCloseTag("betaDistributionModel");

        writer.writeCloseTag("distribution");

        writer.writeOpenTag("data");
        writeParameterReference(writer, parameter);
        writer.writeCloseTag("data");

        writer.writeCloseTag("distributionLikelihood");
    }

    private void writeBetaShapeParameter(
            XMLWriter writer,
            String elementName,
            Parameter parameter,
            String fallbackId
    ) {
        writer.writeOpenTag(elementName);

        writer.writeOpenTag(
                "parameter id=\""
                        + escapeXmlAttribute(fallbackId)
                        + "\" value=\""
                        + format(parameter.getParameterValue(0))
                        + "\" lower=\"0.0\""
        );
        writer.writeCloseTag("parameter");

        writer.writeCloseTag(elementName);
    }

    private void writeTreePriors(
            XMLWriter writer,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, AbstractModelLikelihood>> entries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : entries) {
            writeTreePrior(writer, entry.getKey(), entry.getValue());
        }
    }

    private void writeTreePrior(
            XMLWriter writer,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        if (treePrior instanceof SpeciationLikelihood) {
            writeSpeciationTreePrior(writer, treeModel, treePrior);
            return;
        }

        if (treePrior instanceof CoalescentLikelihood) {
            writeCoalescentTreePrior(writer, treeModel, treePrior);
            return;
        }

        throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
    }

    private void writeSpeciationTreePrior(
            XMLWriter writer,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        writer.writeOpenTag(
                "speciationLikelihood id=\""
                        + escapeXmlAttribute(priorId(treePrior))
                        + "\""
        );

        writer.writeOpenTag("model");

        BirthDeathGernhard08Model birthDeathModel =
                getBirthDeathModel(treePrior);

        if (isYuleCompatible(birthDeathModel)) {
            writer.writeOpenTag(
                    "yuleModel idref=\""
                            + escapeXmlAttribute(treePriorModelId(treePrior))
                            + "\""
            );
            writer.writeCloseTag("yuleModel");
        } else {
            writer.writeOpenTag(
                    "birthDeathModel idref=\""
                            + escapeXmlAttribute(treePriorModelId(treePrior))
                            + "\""
            );
            writer.writeCloseTag("birthDeathModel");
        }

        writer.writeCloseTag("model");

        writer.writeOpenTag("speciesTree");
        writeTreeReference(writer, treeModel);
        writer.writeCloseTag("speciesTree");

        writer.writeCloseTag("speciationLikelihood");
    }

    private void writeCoalescentTreePrior(
            XMLWriter writer,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        writer.writeOpenTag(
                "coalescentLikelihood id=\""
                        + escapeXmlAttribute(priorId(treePrior))
                        + "\""
        );

        writer.writeOpenTag("model");
        writer.writeOpenTag(
                "constantSize idref=\""
                        + escapeXmlAttribute(treePriorModelId(treePrior))
                        + "\""
        );
        writer.writeCloseTag("constantSize");
        writer.writeCloseTag("model");

        writer.writeOpenTag("populationTree");
        writeTreeReference(writer, treeModel);
        writer.writeCloseTag("populationTree");

        writer.writeCloseTag("coalescentLikelihood");
    }

    private void writeOperators(
            XMLWriter writer,
            BeastXState state
    ) {
        writer.writeOpenTag("operators");

        writeParameterOperators(writer, state);
        writeTreeOperators(writer, state);

        writer.writeCloseTag("operators");
    }

    private void writeParameterOperators(
            XMLWriter writer,
            BeastXState state
    ) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(BeastXOfficialXmlWriter::parameterId));

        for (Parameter parameter : parameters) {
            if (hasFiniteLowerAndUpperBounds(parameter)) {
                writeRandomWalkOperator(writer, state, parameter);
            } else {
                writeScaleOperator(writer, state, parameter);
            }
        }
    }

    private void writeScaleOperator(
            XMLWriter writer,
            BeastXState state,
            Parameter parameter
    ) {
        String parameterId =
                parameterId(parameter);

        writer.writeOpenTag(
                "scaleOperator id=\""
                        + escapeXmlAttribute(parameterId)
                        + "_scale\" scaleFactor=\""
                        + format(state.operatorConfig.parameterScaleFactor)
                        + "\" weight=\""
                        + format(state.operatorConfig.parameterOperatorWeight)
                        + "\""
        );

        writeParameterReference(writer, parameter);

        writer.writeCloseTag("scaleOperator");
    }

    private void writeRandomWalkOperator(
            XMLWriter writer,
            BeastXState state,
            Parameter parameter
    ) {
        String parameterId =
                parameterId(parameter);

        writer.writeOpenTag(
                "randomWalkOperator id=\""
                        + escapeXmlAttribute(parameterId)
                        + "_randomWalk\" windowSize=\""
                        + format(state.operatorConfig.randomWalkWindowSize)
                        + "\" weight=\""
                        + format(state.operatorConfig.parameterOperatorWeight)
                        + "\" boundaryCondition=\"reflecting\""
        );

        writeParameterReference(writer, parameter);

        writer.writeCloseTag("randomWalkOperator");
    }

    private void writeTreeOperators(
            XMLWriter writer,
            BeastXState state
    ) {
        List<TreeModel> trees =
                new ArrayList<>(state.treePriorDistributions.keySet());

        trees.sort(Comparator.comparing(BeastXOfficialXmlWriter::treeId));

        for (TreeModel treeModel : trees) {
            String id =
                    treeId(treeModel);

            writeTreeOperator(
                    writer,
                    "narrowExchange",
                    id + "_narrowExchange",
                    state.operatorConfig.treeNarrowExchangeWeight,
                    treeModel
            );

            writeTreeOperator(
                    writer,
                    "wideExchange",
                    id + "_wideExchange",
                    state.operatorConfig.treeWideExchangeWeight,
                    treeModel
            );

            writeSubtreeSlideOperator(writer, state, treeModel);

            writeTreeOperator(
                    writer,
                    "wilsonBalding",
                    id + "_wilsonBalding",
                    state.operatorConfig.treeWilsonBaldingWeight,
                    treeModel
            );
        }
    }

    private void writeTreeOperator(
            XMLWriter writer,
            String elementName,
            String id,
            double weight,
            TreeModel treeModel
    ) {
        writer.writeOpenTag(
                elementName
                        + " id=\""
                        + escapeXmlAttribute(id)
                        + "\" weight=\""
                        + format(weight)
                        + "\""
        );

        writeTreeReference(writer, treeModel);

        writer.writeCloseTag(elementName);
    }

    private void writeSubtreeSlideOperator(
            XMLWriter writer,
            BeastXState state,
            TreeModel treeModel
    ) {
        String id =
                treeId(treeModel);

        writer.writeOpenTag(
                "subtreeSlide id=\""
                        + escapeXmlAttribute(id)
                        + "_subtreeSlide\" weight=\""
                        + format(state.operatorConfig.treeSubtreeSlideWeight)
                        + "\" size=\""
                        + format(state.operatorConfig.treeSubtreeSlideSize)
                        + "\" gaussian=\"true\""
        );

        writeTreeReference(writer, treeModel);

        writer.writeCloseTag("subtreeSlide");
    }

    private void writeLoggers(
            XMLWriter writer,
            BeastXState state
    ) {
        int loggerIndex =
                1;

        for (BeastXState.ScreenLoggerSpec spec : state.screenLoggerSpecs) {
            writeParameterLogger(
                    writer,
                    "screenLogger" + loggerIndex,
                    spec.logEvery,
                    null,
                    getLoggedParameters(state, spec.parameterNames)
            );

            loggerIndex++;
        }

        for (BeastXState.FileLoggerSpec spec : state.fileLoggerSpecs) {
            writeParameterLogger(
                    writer,
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
            writeTreeLogger(
                    writer,
                    "treeLogger" + treeLoggerIndex,
                    spec.logEvery,
                    spec.fileName,
                    getLoggedTrees(state, spec.treeNames)
            );

            treeLoggerIndex++;
        }
    }

    private void writeParameterLogger(
            XMLWriter writer,
            String id,
            long logEvery,
            String fileName,
            List<Parameter> parameters
    ) {
        String tag =
                "log id=\""
                        + escapeXmlAttribute(id)
                        + "\" logEvery=\""
                        + logEvery
                        + "\"";

        if (fileName != null) {
            tag += " fileName=\""
                    + escapeXmlAttribute(fileName)
                    + "\" overwrite=\"true\"";
        }

        writer.writeOpenTag(tag);

        for (Parameter parameter : parameters) {
            writeParameterReference(writer, parameter);
        }

        writer.writeCloseTag("log");
    }

    private void writeTreeLogger(
            XMLWriter writer,
            String id,
            long logEvery,
            String fileName,
            List<TreeModel> treeModels
    ) {
        writer.writeOpenTag(
                "logTree id=\""
                        + escapeXmlAttribute(id)
                        + "\" logEvery=\""
                        + logEvery
                        + "\" fileName=\""
                        + escapeXmlAttribute(fileName)
                        + "\" overwrite=\"true\" nexusFormat=\"true\""
        );

        for (TreeModel treeModel : treeModels) {
            writeTreeReference(writer, treeModel);
        }

        writer.writeCloseTag("logTree");
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

        parameters.sort(Comparator.comparing(BeastXOfficialXmlWriter::parameterId));

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

        trees.sort(Comparator.comparing(BeastXOfficialXmlWriter::treeId));

        return trees;
    }

    private void writeParameterDefinition(
            XMLWriter writer,
            Parameter parameter
    ) {
        String tag =
                "parameter id=\""
                        + escapeXmlAttribute(parameterId(parameter))
                        + "\" value=\""
                        + format(parameter.getParameterValue(0))
                        + "\"";

        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds != null) {
            double lower =
                    bounds.getLowerLimit(0);

            double upper =
                    bounds.getUpperLimit(0);

            if (Double.isFinite(lower)) {
                tag += " lower=\"" + format(lower) + "\"";
            }

            if (Double.isFinite(upper)) {
                tag += " upper=\"" + format(upper) + "\"";
            }
        }

        writer.writeOpenTag(tag);
        writer.writeCloseTag("parameter");
    }

    private void writeInlineParameterDefinition(
            XMLWriter writer,
            String id,
            double value,
            Double lower,
            Double upper
    ) {
        String tag =
                "parameter id=\""
                        + escapeXmlAttribute(id)
                        + "\" value=\""
                        + format(value)
                        + "\"";

        if (lower != null) {
            tag += " lower=\"" + format(lower) + "\"";
        }

        if (upper != null) {
            tag += " upper=\"" + format(upper) + "\"";
        }

        writer.writeOpenTag(tag);
        writer.writeCloseTag("parameter");
    }

    private void writeParameterReference(
            XMLWriter writer,
            Parameter parameter
    ) {
        writer.writeOpenTag(
                "parameter idref=\""
                        + escapeXmlAttribute(parameterId(parameter))
                        + "\""
        );

        writer.writeCloseTag("parameter");
    }

    private void writeTreeReference(
            XMLWriter writer,
            TreeModel treeModel
    ) {
        writer.writeOpenTag(
                "treeModel idref=\""
                        + escapeXmlAttribute(treeId(treeModel))
                        + "\""
        );

        writer.writeCloseTag("treeModel");
    }

    private static BirthDeathGernhard08Model getBirthDeathModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof SpeciationLikelihood speciationLikelihood)) {
            throw unsupported("Only SpeciationLikelihood tree priors can be serialized as Yule or BirthDeath XML.");
        }

        SpeciationModel speciationModel =
                speciationLikelihood.getSpeciationModel();

        if (!(speciationModel instanceof BirthDeathGernhard08Model birthDeathModel)) {
            throw unsupported("Only Yule and BirthDeath tree priors are supported.");
        }

        return birthDeathModel;
    }

    private static ConstantPopulationModel getConstantPopulationModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof CoalescentLikelihood coalescentLikelihood)) {
            throw unsupported("Only CoalescentLikelihood tree priors can be serialized as coalescent XML.");
        }

        DemographicModel demographicModel =
                coalescentLikelihood.getDemoModel();

        if (!(demographicModel instanceof ConstantPopulationModel constantPopulationModel)) {
            throw unsupported("Only constant-population Coalescent tree priors are supported.");
        }

        return constantPopulationModel;
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

    private static Parameter betaDistributionVariable(
            BetaDistributionModel distribution,
            int variableIndex
    ) {
        if (variableIndex >= distribution.getVariableCount()) {
            throw unsupported("Beta XML export requires alpha and beta parameters.");
        }

        Variable<?> variable =
                distribution.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        throw unsupported("Beta XML export requires alpha and beta parameters.");
    }

    private static Parameter constantPopulationVariable(ConstantPopulationModel populationModel) {
        if (populationModel.getVariableCount() < 1) {
            throw unsupported("Constant-population Coalescent XML export requires a population size parameter.");
        }

        Variable<?> variable =
                populationModel.getVariable(0);

        if (!(variable instanceof Parameter parameter)) {
            throw unsupported("Constant-population Coalescent XML export requires a population size parameter.");
        }

        return parameter;
    }

    private static boolean isYuleCompatible(BirthDeathGernhard08Model model) {
        return model.isYule()
                || (
                approximatelyZero(model.getA())
                        && approximatelyOne(model.getRho())
        );
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
                message + " Extend BeastXOfficialXmlWriter before exporting this model class to XML."
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

    private static String escapeXmlAttribute(String text) {
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}