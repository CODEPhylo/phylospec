package tiles.input;

import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beastconfig.BEASTState;
import java.util.AbstractList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.phylospec.tiling.errors.TileApplicationError;

/** A read-only taxon list with the corresponding native tree-model inputs. */
public final class TaxaData extends AbstractList<TaxonValue> {
    private final List<TaxonValue> values;
    private final TaxonSet taxonSet;
    private final TraitSet ages;

    /** Preserve the effective sampling ages and native metadata of imported data. */
    public TaxaData(TaxonSet taxonSet, TraitSet ages) {
        this(taxonSet, ages, Map.of());
    }

    public TaxaData(TaxonSet taxonSet, TraitSet ages, Map<String, String> speciesNames) {
        this(
                taxonSet.asStringList().stream()
                        .map(name ->
                                new TaxonValue(name, speciesNames.get(name), ages == null ? 0.0 : ages.getValue(name)))
                        .toList(),
                taxonSet,
                ages);
    }

    private TaxaData(List<TaxonValue> values, TaxonSet taxonSet, TraitSet ages) {
        this.values = List.copyOf(values);
        this.taxonSet = taxonSet;
        this.ages = ages;
    }

    @Override
    public TaxonValue get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    public TaxonSet taxonSet() {
        return taxonSet;
    }

    public TraitSet ages() {
        return ages;
    }

    /** Build native inputs at the tree-model boundary, reusing imported inputs when available. */
    public static TaxaData fromList(BEASTState state, List<TaxonValue> values) {
        if (values instanceof TaxaData data) {
            return data;
        }
        if (values.isEmpty()) {
            throw new TileApplicationError("No taxa found.", "A tree model requires a nonempty taxon list.");
        }

        Set<String> seen = new HashSet<>();
        double minimumAge = Double.POSITIVE_INFINITY;
        double maximumAge = 0.0;
        for (TaxonValue taxon : values) {
            if (!seen.add(taxon.name())) {
                throw new TileApplicationError(
                        "Duplicate taxon name.",
                        "A tree model requires unique names; '" + taxon.name() + "' occurs more than once.");
            }
            minimumAge = Math.min(minimumAge, taxon.age());
            maximumAge = Math.max(maximumAge, taxon.age());
        }
        if (minimumAge != 0.0) {
            // Native TraitSet and Coalescent intervals use the youngest sample as
            // their origin. Do not silently reinterpret manually supplied ages.
            throw new TileApplicationError(
                    "Unsupported sampling-age origin.",
                    "Tree models currently require at least one sample at age zero. The supplied taxon ages have not been changed.");
        }

        TaxonSet taxonSet = new TaxonSet(
                Taxon.createTaxonList(values.stream().map(TaxonValue::name).toList()));
        TraitSet ages = null;
        if (maximumAge > 0.0) {
            StringJoiner entries = new StringJoiner(",");
            for (TaxonValue taxon : values) {
                String name = taxon.name();
                if (name.contains(",")
                        || name.contains("=")
                        || !name.equals(name.replaceAll("\\s+", " ").strip())) {
                    throw new TileApplicationError(
                            "Unsupported taxon name for sampling ages.",
                            "Taxon '" + name
                                    + "' cannot be represented without changing its name in BEAST's TraitSet.");
                }
                entries.add(name + "=" + taxon.age());
            }

            ages = new TraitSet();
            state.setInput(ages, ages.traitNameInput, "age");
            state.setInput(ages, ages.taxaInput, taxonSet);
            state.setInput(ages, ages.traitsInput, entries.toString());
            ages.initAndValidate();
        }
        return new TaxaData(values, taxonSet, ages);
    }
}
