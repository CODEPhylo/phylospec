module org.phylospec.core {
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.core;
    requires transitive com.fasterxml.jackson.databind;
    requires com.fasterxml.classmate;
    requires com.github.victools.jsonschema.generator;
    requires java.compiler;
    requires transitive org.eclipse.lsp4j;
    requires transitive org.eclipse.lsp4j.jsonrpc;

    exports org.phylospec;
    exports org.phylospec.ast;
    exports org.phylospec.ast.transformers;
    exports org.phylospec.components;
    exports org.phylospec.domain;
    exports org.phylospec.errors;
    exports org.phylospec.lexer;
    exports org.phylospec.parser;
    exports org.phylospec.templatematching;
    exports org.phylospec.tiling;
    exports org.phylospec.tiling.errors;
    exports org.phylospec.tiling.tiles;
    exports org.phylospec.typeresolver;
    exports org.phylospec.types;

    uses org.phylospec.tiling.TileLibrary;
    uses org.phylospec.typeresolver.properties.GeneratorPropertyProvider;

    provides org.phylospec.typeresolver.properties.GeneratorPropertyProvider with
            org.phylospec.typeresolver.properties.FromCsvProvider,
            org.phylospec.typeresolver.properties.FromFastaProvider,
            org.phylospec.typeresolver.properties.FromNewickProvider,
            org.phylospec.typeresolver.properties.FromNexusProvider,
            org.phylospec.typeresolver.properties.FromTreeProvider;
}
