package tiling.xml;

import dr.app.beast.BeastParser;
import dr.app.beast.BeastVersion;
import dr.inference.mcmc.MCMC;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BeastXXmlRunner {

    public MCMC parse(Path xmlPath) throws Exception {
        BeastParser parser =
                new BeastParser(
                        new String[0],
                        List.of(),
                        false,
                        false,
                        false,
                        BeastVersion.INSTANCE
                );

        try (Reader reader = Files.newBufferedReader(xmlPath, StandardCharsets.UTF_8)) {
            Object parsed =
                    parser.parse(reader, MCMC.class);

            if (!(parsed instanceof MCMC mcmc)) {
                throw new IllegalStateException(
                        "BEAST X XML did not parse to an MCMC object: " + parsed
                );
            }

            return mcmc;
        }
    }

    public MCMC run(Path xmlPath) throws Exception {
        MCMC mcmc =
                parse(xmlPath);

        mcmc.run();

        return mcmc;
    }
}