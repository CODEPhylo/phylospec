package tiles.misc;

import dr.evolution.util.Date;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BeastXState;
import tiling.WiredInputs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Taxa from a list of taxa, as in Taxa taxa = [taxon(name="a"), taxon(name="b", age=2.0)].
 *
 * <p>The tree priors take a BEAST X {@link Taxa}, which the generic list tile does not build.
 * As in BEAST 3, ages are shifted so that the youngest taxon has age 0: BEAST X measures tip
 * heights from the most recent date it has seen anywhere in the JVM, so without a taxon at age 0
 * the same taxa would get different tip heights depending on what was built before. The shifted
 * ages are set on copies, so age(taxon) of the original taxon keeps its given age.</p>
 */
public class TaxonListTile extends AstNodeTile<Taxa, Expr.Array, BeastXState> {

    private static final TypeToken<Taxon> TAXON = new TypeToken<>() {};

    private final List<Tile<?, BeastXState>> inputTiles;

    public TaxonListTile() {
        this.inputTiles = new ArrayList<>();
    }

    private TaxonListTile(List<Tile<?, BeastXState>> inputTiles) {
        this.inputTiles = inputTiles;
    }

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array) || array.elements.isEmpty()) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (!this.getCompatibleStochasticities().contains(stochasticityResolver.getStochasticity(node))) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        List<Set<Tile<?, BeastXState>>> taxonTiles =
                new ArrayList<>();

        for (Expr element : array.elements) {
            Set<Tile<?, BeastXState>> tiles =
                    new HashSet<>();

            for (Tile<?, BeastXState> tile : allInputTiles.get(element)) {
                if (TAXON.equals(tile.getTypeToken())) {
                    tiles.add(tile);
                }
            }

            if (tiles.isEmpty()) {
                throw new FailedTilingAttempt.Irrelevant();
            }

            taxonTiles.add(tiles);
        }

        Set<Tile<?, BeastXState>> result =
                new HashSet<>();

        Utils.visitCombinations(
                taxonTiles,
                inputTiles -> {
                    Tile<?, BeastXState> tile =
                            new TaxonListTile(inputTiles);

                    tile.setRootNode(node);
                    tile.setWeight(
                            inputTiles.stream().mapToInt(Tile::getWeight).sum() + this.getPriority().getWeight()
                    );

                    result.add(tile);
                }
        );

        return result;
    }

    @Override
    public Map<AstNode, Tile<?, ?>> getWiredUpInputs() throws InconsistentTilingException {
        return WiredInputs.of(this.inputTiles);
    }

    @Override
    public Taxa applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<Taxon> taxa =
                new ArrayList<>();

        Set<String> names =
                new HashSet<>();

        double youngestAge =
                Double.POSITIVE_INFINITY;

        for (Tile<?, BeastXState> tile : this.inputTiles) {
            Taxon taxon =
                    (Taxon) tile.apply(beastState, indexVariables);

            if (!names.add(taxon.getId())) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "Taxon '" + taxon.getId() + "' appears twice.",
                        "Give each taxon a distinct name."
                );
            }

            taxa.add(taxon);
            youngestAge = Math.min(youngestAge, age(taxon));
        }

        Taxa result =
                new Taxa();

        for (Taxon taxon : taxa) {
            Taxon copy =
                    new Taxon(taxon.getId());

            copy.setDate(Date.createRelativeAge(age(taxon) - youngestAge, Units.Type.YEARS));

            if (taxon.getAttribute("species") != null) {
                copy.setAttribute("species", taxon.getAttribute("species"));
            }

            result.addTaxon(copy);
        }

        return result;
    }

    private static double age(Taxon taxon) {
        return taxon.getDate() == null ? 0.0 : taxon.getDate().getTimeValue();
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<Taxa>() {};
    }
}
