package tiles.phylonco;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The counting rules must match LPhy's PileupSite.translateRead. */
class MpileupReaderTest {

    @Test
    void countsMatchesMismatchesAndSkipsMarkers() {
        // ref = G (2): '.' and ',' count as G; '^' + quality, '$', indels, '*' are skipped; case is ignored
        int[] counts = MpileupReader.countBases(2, "^I.,a$T+2AC-1g*,<t>.");
        assertArrayEquals(new int[] {1, 0, 4, 2}, counts);
    }

    @Test
    void readsCellsInColumnOrderAndSkipsShortLines(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("x.mpileup");
        Files.writeString(file, String.join("\n",
                "chr1\t10\tA\t3\t.,C\tIII\t2\t^I.T$\tII",
                "chr1\t11\tC\t1\t.\tI", // too few columns for two cells: skipped entirely
                "chr1\t12\tt\t2\tGG\tII\t0\t*\tI",
                ""));

        MpileupReader.Counts counts = MpileupReader.read(file, List.of("a", "b"));

        assertEquals(2, counts.numSites());
        assertEquals(List.of("chr1:10", "chr1:12"), counts.sites());
        assertEquals("2:1:0:0,0:0:2:0\n1:0:0:1,0:0:0:0", counts.toReadCountString());
    }

    @Test
    void rejectsMoreCellColumnsThanNames(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("x.mpileup");
        Files.writeString(file, "chr1\t10\tA\t3\t.,C\tIII\t2\t^I.T$\tII\n");

        IOException error = assertThrows(IOException.class, () -> MpileupReader.read(file, List.of("a")));
        assertTrue(error.getMessage().contains("2 cell columns"), error.getMessage());
        assertTrue(error.getMessage().contains("1 cells"), error.getMessage());
    }

    @Test
    void reportsAMalformedIndelWithItsLineAndCell(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("x.mpileup");
        Files.writeString(file, "chr1\t10\tA\t3\t.,C\tIII\t2\t.+A\tII\n");

        IOException error = assertThrows(IOException.class, () -> MpileupReader.read(file, List.of("a", "b")));
        assertTrue(error.getMessage().contains("Line 1"), error.getMessage());
        assertTrue(error.getMessage().contains("'b'"), error.getMessage());
    }
}
