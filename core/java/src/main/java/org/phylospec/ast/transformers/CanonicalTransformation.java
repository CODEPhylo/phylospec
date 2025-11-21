package org.phylospec.ast.transformers;


import org.phylospec.ast.AstTransformer;

/**
 * This transformation converts a syntax tree into its canonical form.
 * Every canonical statement nests at most two levels (e.g. a literal or an array of variables).
 */
public class CanonicalTransformation extends AstTransformer {

}
