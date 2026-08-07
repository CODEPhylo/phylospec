package tiling.xml;

import dr.app.beast.BeastParser;
import dr.app.beast.BeastVersion;
import dr.inference.mcmc.MCMC;

import tiling.runner.BeagleBackendConfigurator;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs BEAST X XML through the XML parser and MCMC execution path.
 *
 * Used to check that exported XML can be parsed and executed by BEAST X.
 */
public class XmlRunner {

    public MCMC parse(Path xmlPath) throws Exception {
        if (xmlPath == null) {
            throw new IllegalArgumentException(
                    "xmlPath must not be null."
            );
        }

        String xml = Files.readString(xmlPath, StandardCharsets.UTF_8);
        if (requiresNativeBeagle(xml)) {
            BeagleBackendConfigurator.requireNativeBackend();
        }

        BeastParser parser =
                new BeastParser(
                        new String[0],
                        List.of(),
                        false,
                        false,
                        false,
                        BeastVersion.INSTANCE
                );

        try (StringReader reader = new StringReader(xml)) {
            Object parsed =
                    parser.parse(reader, MCMC.class);

            if (!(parsed instanceof MCMC mcmc)) {
                throw new IllegalStateException(
                        "BEAST X XML did not parse to an MCMC object: "
                                + parsed
                );
            }

            return mcmc;
        }
    }

    static boolean requiresNativeBeagle(String xml) {
        return xml.contains("<treeLikelihood")
                || xml.contains("<ancestralTreeLikelihood")
                || xml.contains("<markovJumpsTreeLikelihood");
    }

    public MCMC run(Path xmlPath) throws Exception {
        MCMC mcmc =
                parse(xmlPath);

        mcmc.run();

        return mcmc;
    }
}
