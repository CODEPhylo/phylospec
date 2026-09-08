open module org.phylospec.beast3.popfunc {
    requires org.phylospec.beast3;
    requires popfunc.beast;

    provides org.phylospec.tiling.TileLibrary with
            tiles.popfunc.PopFuncTileLibrary;
}
