open module org.phylospec.beast3.phylonco {
    requires org.phylospec.beast3;
    requires phylonco.beast;

    provides org.phylospec.tiling.TileLibrary with
            tiles.phylonco.PhyloncoTileLibrary;
}
