package tiling.xml;

import tiling.BeastXModel;
import tiling.BeastXState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StateXmlGenerator {

    private final XmlPlanBuilder planBuilder =
            new XmlPlanBuilder();

    private final XmlDocumentWriter documentWriter =
            new XmlDocumentWriter();

    public void write(
            BeastXModel model,
            Path path
    ) throws IOException {
        Path parent =
                path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, toXml(model), StandardCharsets.UTF_8);
    }

    public String toXml(BeastXModel model) {
        XmlPlan plan =
                model.beastState.xmlPlan.isEmpty()
                        ? planBuilder.build(model)
                        : model.beastState.xmlPlan;

        return documentWriter.write(model.beastState, plan);
    }

    public String toXml(BeastXState state) {
        return documentWriter.write(state, state.xmlPlan);
    }
}