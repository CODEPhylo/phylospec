package tiles.input;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.tiling.errors.TileApplicationError;

class TaxaDataTest {

    @Test
    void manualTaxaPreserveOrderSpeciesAndSamplingAgesWithoutAddingSampledState() {
        List<TaxonValue> values = List.of(
                new TaxonValue("sample_C", "species_X", 0.0),
                new TaxonValue("sample_A", "species_X", 1.0),
                new TaxonValue("sample_B", "species_Y", 2.0));
        BEASTState graph = new BEASTState("manual-taxa");
        TaxaData data = TaxaData.fromList(graph, values);
        graph.initializeBEASTObjects();

        assertEquals(values, data);
        assertEquals(
                List.of("sample_C", "sample_A", "sample_B"), data.taxonSet().asStringList());
        assertNull(data.taxonSet().alignmentInput.get());
        assertNotNull(data.ages());
        assertSame(data.taxonSet(), data.ages().taxaInput.get());
        assertEquals("age", data.ages().getTraitName());
        for (TaxonValue value : values) {
            assertEquals(value.age(), data.ages().getValue(value.name()));
            assertEquals(value.age(), data.ages().getDate(value.age()));
        }
        assertEquals("species_X", data.get(0).species());
        assertEquals("species_X", data.get(1).species());
        assertEquals("species_Y", data.get(2).species());
        assertNoSampledState(graph);
    }

    @Test
    void decimalAgesSurviveRepeatedNativeInitializationWithoutChangingTheTimeOrigin() {
        List<TaxonValue> values = List.of(
                new TaxonValue("a", null, 0.0), new TaxonValue("b", null, 0.1), new TaxonValue("c", null, 2.75));
        BEASTState graph = new BEASTState("decimal-taxa");
        TaxaData data = TaxaData.fromList(graph, values);

        for (int iteration = 0; iteration < 3; iteration++) {
            data.ages().initAndValidate();
            graph.initializeBEASTObjects();
            assertEquals(values, data);
            for (TaxonValue value : values) {
                assertEquals(value.age(), data.ages().getValue(value.name()));
            }
            assertEquals(4.25, data.ages().getDate(4.25));
        }
    }

    @Test
    void allPositiveAgesRemainValidValuesButCannotSilentlyShiftAtTheModelBoundary() {
        List<TaxonValue> values = List.of(new TaxonValue("a", null, 2.0), new TaxonValue("b", null, 3.0));
        BEASTState graph = new BEASTState("positive-origin");

        assertEquals(2.0, values.get(0).age());
        assertEquals(3.0, values.get(1).age());
        assertThrows(TileApplicationError.class, () -> TaxaData.fromList(graph, values));
        assertEquals(2.0, values.get(0).age());
        assertEquals(3.0, values.get(1).age());
        assertNoSampledState(graph);
    }

    @Test
    void manualDataCopiesItsInputAndExposesAnImmutableList() {
        TaxonValue a = new TaxonValue("a", null, 0.0);
        TaxonValue b = new TaxonValue("b", "species_X", 1.0);
        List<TaxonValue> input = new ArrayList<>(List.of(a, b));
        TaxaData data = TaxaData.fromList(new BEASTState("immutable-taxa"), input);
        input.clear();

        assertEquals(List.of(a, b), data);
        assertEquals(List.of("a", "b"), data.taxonSet().asStringList());
        assertThrows(UnsupportedOperationException.class, () -> data.add(a));
        assertThrows(UnsupportedOperationException.class, () -> data.set(0, b));
        assertThrows(UnsupportedOperationException.class, () -> data.remove(0));
    }

    @Test
    void alreadyAdaptedTaxaRetainTheirNativeObjects() {
        BEASTState graph = new BEASTState("reused-taxa");
        TaxaData data =
                TaxaData.fromList(graph, List.of(new TaxonValue("a", null, 0.0), new TaxonValue("b", null, 1.0)));

        assertSame(data, TaxaData.fromList(graph, data));
    }

    @Test
    void contemporaryNativeTaxaExposeZeroAgesWithoutInventingSpecies() {
        TaxonSet taxa = new TaxonSet(Taxon.createTaxonList(List.of("b", "a")));
        TaxaData data = new TaxaData(taxa, null);

        assertSame(taxa, data.taxonSet());
        assertNull(data.ages());
        assertEquals(List.of(new TaxonValue("b", null, 0.0), new TaxonValue("a", null, 0.0)), data);
        assertNull(data.get(0).species());
        assertNull(data.get(1).species());
    }

    @ParameterizedTest
    @CsvSource({"age,2,3,0.0,1.0", "date,2000,2002,2.0,0.0"})
    void importedAlignmentMetadataRetainsNativeIdentityAndEffectiveModelAges(
            String kind, String first, String second, double firstAge, double secondAge) {
        Alignment alignment =
                new Alignment(List.of(new Sequence("a", "ACGT"), new Sequence("b", "ACGA")), "nucleotide");
        TaxonSet taxa = new TaxonSet(alignment);
        TraitSet ages = new TraitSet();
        ages.initByName("traitname", kind, "taxa", taxa, "value", "a=" + first + ",b=" + second);
        TaxaData data = new TaxaData(taxa, ages);

        assertSame(taxa, data.taxonSet());
        assertSame(ages, data.ages());
        assertSame(alignment, data.taxonSet().alignmentInput.get());
        assertEquals(first, ages.getStringValue("a"));
        assertEquals(second, ages.getStringValue("b"));
        assertEquals(firstAge, data.get(0).age());
        assertEquals(secondAge, data.get(1).age());
        assertEquals(ages.getValue("a"), data.get(0).age());
        assertEquals(ages.getValue("b"), data.get(1).age());
        assertNull(data.get(0).species());
        assertNull(data.get(1).species());
        assertSame(data, TaxaData.fromList(new BEASTState("imported-taxa"), data));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingNamesAtTaxonConstruction(String name) {
        assertThrows(IllegalArgumentException.class, () -> new TaxonValue(name, null, 0.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidAgesAtTaxonConstruction(double age) {
        assertThrows(IllegalArgumentException.class, () -> new TaxonValue("a", null, age));
    }

    @Test
    void duplicateNamesAreRejectedWhenAListBecomesTreeModelInput() {
        List<TaxonValue> values = List.of(new TaxonValue("a", "species_X", 0.0), new TaxonValue("a", "species_Y", 1.0));

        assertEquals(2, values.size());
        assertThrows(TileApplicationError.class, () -> TaxaData.fromList(new BEASTState("duplicate-taxa"), values));
    }

    @Test
    void anEmptyListCannotBecomeTreeModelInput() {
        assertThrows(TileApplicationError.class, () -> TaxaData.fromList(new BEASTState("empty-taxa"), List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a,b", "a=b", " a", "a  b"})
    void rejectsNamesThatNativeAgeTraitsCannotPreserve(String name) {
        List<TaxonValue> values = List.of(new TaxonValue(name, null, 0.0), new TaxonValue("b", null, 1.0));
        assertThrows(TileApplicationError.class, () -> TaxaData.fromList(new BEASTState("trait-encoding"), values));
    }

    @Test
    void contemporaryNamesDoNotNeedTraitSerialization() {
        List<TaxonValue> values = List.of(new TaxonValue("a,b", null, 0.0), new TaxonValue("a=b", null, 0.0));
        TaxaData data = TaxaData.fromList(new BEASTState("contemporary-names"), values);

        assertEquals(values, data);
        assertEquals(List.of("a,b", "a=b"), data.taxonSet().asStringList());
        assertNull(data.ages());
    }

    private static void assertNoSampledState(BEASTState graph) {
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }
}
