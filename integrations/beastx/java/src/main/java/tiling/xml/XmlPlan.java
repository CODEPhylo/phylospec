package tiling.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class XmlPlan {

    private final Map<Section, List<XmlElement>> sections =
            new EnumMap<>(Section.class);

    public XmlPlan() {
        for (Section section : Section.values()) {
            sections.put(section, new ArrayList<>());
        }
    }

    public void add(Section section, XmlElement element) {
        sections.get(section).add(element);
    }

    public void addAll(Section section, List<XmlElement> elements) {
        sections.get(section).addAll(elements);
    }

    public List<XmlElement> get(Section section) {
        return Collections.unmodifiableList(sections.get(section));
    }

    public boolean has(Section section) {
        return !sections.get(section).isEmpty();
    }

    public boolean isEmpty() {
        for (List<XmlElement> elements : sections.values()) {
            if (!elements.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public enum Section {
        BEFORE_TAXA,
        PARAMETERS,
        TAXA,
        TAXON_SETS,
        ALIGNMENTS,
        PATTERN_LISTS,
        TREE_PRIOR_MODELS,
        STARTING_TREES,
        TREE_MODELS,
        STATISTICS,
        TREE_PRIOR_LIKELIHOODS,
        BRANCH_RATE_MODELS,
        SUBSTITUTION_SITE_MODELS,
        CLOCK_PARAMETERS,
        TREE_LIKELIHOODS,
        OPERATORS,
        MCMC_PRIOR,
        MCMC_LIKELIHOOD,
        MCMC_LOGGERS,
        AFTER_MCMC
    }
}