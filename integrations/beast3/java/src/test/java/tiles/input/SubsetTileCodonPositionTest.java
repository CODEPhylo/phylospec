package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.spec.evolution.alignment.FilteredAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubsetTileCodonPositionTest {

    @Test
    public void buildsBeastIteratorFiltersForCodonPositions() {
        assertEquals("1::3", SubsetTile.buildFilterString(null, null, 1));
        assertEquals("2::3", SubsetTile.buildFilterString(null, null, 2));
        assertEquals("3::3", SubsetTile.buildFilterString(null, null, 3));
    }

    @Test
    public void preservesGlobalCodonPositionWithinARange() {
        assertEquals("7:12:3", SubsetTile.buildFilterString(5, 12, 1));
        assertEquals("5:12:3", SubsetTile.buildFilterString(5, 12, 2));
        assertEquals("6:12:3", SubsetTile.buildFilterString(5, 12, 3));
        assertEquals("5-12", SubsetTile.buildFilterString(5, 12, null));
    }

    @Test
    public void partitionsTheSrd06AlignmentIntoExpectedSiteCounts() {
        Alignment alignment = alignmentWithSiteCount(898);

        assertEquals(300, filteredSiteCount(alignment, 1));
        assertEquals(299, filteredSiteCount(alignment, 2));
        assertEquals(299, filteredSiteCount(alignment, 3));
    }

    private static Alignment alignmentWithSiteCount(int siteCount) {
        String sequence =
                "ACGT"
                        .repeat((siteCount + 3) / 4)
                        .substring(0, siteCount);

        Alignment alignment = new Alignment();
        alignment.initByName(
                "sequence", new Sequence("taxon1", sequence),
                "sequence", new Sequence("taxon2", sequence),
                "dataType", "nucleotide"
        );

        return alignment;
    }

    private static int filteredSiteCount(Alignment alignment, int codonPosition) {
        FilteredAlignment filteredAlignment = new FilteredAlignment();
        filteredAlignment.initByName(
                "data", alignment,
                "filter", SubsetTile.buildFilterString(null, null, codonPosition)
        );

        return filteredAlignment.getSiteCount();
    }
}
