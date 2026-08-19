import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Test3 {

    static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        Path path = Path.of("/Users/ochsneto/Documents/PhyloSpec/phylospec/examples/model.phylospec");
        String source = Files.readString(path, StandardCharsets.UTF_8);
        PhyloSpecRunner parser = new PhyloSpecRunner(source);
        parser.runPhyloSpec(path.getFileName().toString());
    }

}
