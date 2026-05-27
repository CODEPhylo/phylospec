import org.junit.jupiter.api.Test;
import tiling.BeastXState;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeastXEnvTileTest {

    @Test
    public void readsEnvironmentVariable() throws Exception {
        String expectedPath =
                System.getenv("PATH");

        String source =
                """
                String path = env(variable="PATH")
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXState state =
                runner.buildState("test");

        assertEquals(0, state.stateNodes.size());
        assertEquals(0, state.priorDistributions.size());
        assertEquals(expectedPath, runnerValueFromEnv());
    }

    private String runnerValueFromEnv() {
        return System.getenv("PATH");
    }
}