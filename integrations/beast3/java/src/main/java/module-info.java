module org.phylospec.beast3 {
    requires transitive beast.base;
    requires beast.pkgmgmt;
    requires java.xml;
    requires org.apache.commons.math4.legacy;
    requires transitive org.phylospec.core;

    opens tiles to org.phylospec.core;
    opens tiles.branchmodels to org.phylospec.core;
    opens tiles.distributions to org.phylospec.core;
    opens tiles.errors to org.phylospec.core;
    opens tiles.functions to org.phylospec.core;
    opens tiles.input to org.phylospec.core;
    opens tiles.mcmc to org.phylospec.core;
    opens tiles.misc to org.phylospec.core;
    opens tiles.observations to org.phylospec.core;
    opens tiles.operators to org.phylospec.core;
    opens tiles.rpn to org.phylospec.core;
    opens tiles.sitemodels to org.phylospec.core;
    opens tiles.substitutionmodels to org.phylospec.core;
    opens tiles.trees to org.phylospec.core;
    exports beastconfig;
    exports tiling;
    exports runner;

    provides org.phylospec.tiling.TileLibrary with tiles.BeastCoreTileLibrary;
}
