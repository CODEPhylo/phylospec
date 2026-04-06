package tiles.functions;

import beast.base.evolution.alignment.Alignment;
import beast.base.parser.NexusParser;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;
import tiling.TilingError;

import java.io.File;
import java.io.IOException;

public class FromNexusTile extends GeneratorTile<Alignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNexus";
    }

    Input<String> fileInput = new Input<>("file");
    Input<ParserTile.Parser> ageInput = new Input<>("age", false);
    Input<ParserTile.Parser> speciesNameInput = new Input<>("speciesName", false);

    @Override
    public Alignment applyTile(BEASTState beastState) {
        String path = this.fileInput.apply(beastState);
        File file = new File(path);

        NexusParser nexusParser = new NexusParser();
        try {
            nexusParser.parseFile(file);
        } catch (IOException e) {
            throw new TilingError(
                    "File not found.",
                    "'" + path + "' could not be found. Does it exist? Select a valid file path."
            );
        }

        // TODO

        return nexusParser.m_alignment;
    }

    @Override
    protected Tile<?> createInstance() {
        return new FromNexusTile();
    }

}
