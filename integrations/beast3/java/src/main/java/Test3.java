import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Test3 {

    static void main(String[] args) throws IOException {
        String source = Files.readString(Path.of("/Users/ochsneto/Documents/PhyloSpec/phylospec/examples/model.phylospec"), StandardCharsets.UTF_8);
        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec();
    }

}
