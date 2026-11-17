package tiling.xml;

import dr.app.beast.BeastParser;
import dr.app.beast.BeastVersion;
import dr.evomodelxml.tree.TreeLoggerParser;
import dr.inference.mcmc.MCMC;
import dr.inferencexml.loggers.LoggerParser;
import dr.xml.XMLObject;
import dr.xml.XMLParseException;

import java.io.Reader;
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
        return parse(xmlPath, newParser());
    }

    /** Load the analysis to verify it, without letting native logger constructors open the user's files. */
    public void validateExport(Path xmlPath) throws Exception {
        BeastParser parser = newParser();
        parser.addXMLObjectParser(new LoggerParser() {
            @Override
            public Object parseXMLObject(XMLObject logger) throws XMLParseException {
                suppressFileOutput(logger);
                return super.parseXMLObject(logger);
            }
        }, true);
        parser.addXMLObjectParser(new TreeLoggerParser() {
            @Override
            public Object parseXMLObject(XMLObject logger) throws XMLParseException {
                suppressFileOutput(logger);
                return super.parseXMLObject(logger);
            }
        }, true);
        parse(xmlPath, parser);
    }

    private static void suppressFileOutput(XMLObject logger) {
        // fileName is optional for both native parsers. Their screen sinks only write when the chain runs.
        // This changes the parser's private DOM, not the staged XML that is delivered to the user.
        if (logger.hasAttribute(LoggerParser.FILE_NAME)) {
            logger.getAttributes().removeNamedItem(LoggerParser.FILE_NAME);
        }
    }

    private static BeastParser newParser() {
        return new BeastParser(new String[0], List.of(), false, false, false, BeastVersion.INSTANCE);
    }

    private static MCMC parse(Path xmlPath, BeastParser parser) throws Exception {
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