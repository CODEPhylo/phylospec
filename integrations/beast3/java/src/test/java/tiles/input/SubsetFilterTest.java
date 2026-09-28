package tiles.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.spec.evolution.alignment.FilteredAlignment;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The filter strings {@code subset} hands to BEAST, and the sites BEAST then keeps.
 *
 * <p>A slash is not a step in BEAST's filter syntax: {@code 1-/2} kept every site, so each
 * codon-position partition held the whole alignment. These tests read the sites back from a
 * real {@link FilteredAlignment}, not just the string.
 */
public class SubsetFilterTest {

    @Test
    public void testFilterStrings() {
        assertEquals("1-", SubsetTile.filterString(null, null, null));
        assertEquals("5-20", SubsetTile.filterString(5, 20, null));
        assertEquals("1::3", SubsetTile.filterString(null, null, 1));
        assertEquals("2::3", SubsetTile.filterString(null, null, 2));
        assertEquals("3::3", SubsetTile.filterString(null, null, 3));
        assertEquals("6:20:3", SubsetTile.filterString(5, 20, 2));
    }

    @Test
    public void testCodonPositionsPartitionTheSites() {
        // 10 sites: positions 1, 2 and 3 are sites {1,4,7,10}, {2,5,8} and {3,6,9}
        Alignment alignment = alignment(10);

        assertArrayEquals(new int[] {0, 3, 6, 9}, keptSites(alignment, SubsetTile.filterString(null, null, 1)));
        assertArrayEquals(new int[] {1, 4, 7}, keptSites(alignment, SubsetTile.filterString(null, null, 2)));
        assertArrayEquals(new int[] {2, 5, 8}, keptSites(alignment, SubsetTile.filterString(null, null, 3)));
    }

    @Test
    public void testCodonPositionWithinARange() {
        // sites 4..9, position 2 of that range: sites 5 and 8 (1-based)
        Alignment alignment = alignment(10);
        assertArrayEquals(new int[] {4, 7}, keptSites(alignment, SubsetTile.filterString(4, 9, 2)));
    }

    @Test
    public void testRangeWithoutCodonPosition() {
        Alignment alignment = alignment(10);
        assertArrayEquals(
                IntStream.rangeClosed(2, 5).toArray(), keptSites(alignment, SubsetTile.filterString(3, 6, null)));
    }

    private static int[] keptSites(Alignment alignment, String filter) {
        FilteredAlignment filtered = new FilteredAlignment();
        filtered.initByName("data", alignment, "filter", filter);
        return filtered.indices();
    }

    private static Alignment alignment(int numSites) {
        String bases = "ACGT".repeat(numSites).substring(0, numSites);
        return new Alignment(List.of(new Sequence("a", bases), new Sequence("b", bases)), 4, "nucleotide");
    }
}
