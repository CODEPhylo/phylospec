# testGTR

## Reference

This example starts from the BEAST 2 `testGTR.xml` example and uses the
existing aligned reference XML for strict posterior comparison.

- Original XML:
  `integrations/beast3/java/src/test/java/resources/comparison/examples/testGTR/beast2-testGTR.xml`
- Validation reference:
  `validation/examples/testGTR/reference/beast2-aligned.xml`

The validation reference is a preserved copy of the aligned BEAST 2 model.
Keeping it beside `testGTR.phylospec` makes this example self-contained while
the original comparison resources remain in place for existing integration
tests.

## Manual PhyloSpec translation

`testGTR.phylospec` is intentionally maintained by hand. The validation workflow
only materializes the `${REPOSITORY_ROOT}` path placeholder; it does not infer
or rewrite the model from BEAST XML.

The represented model contains:

- one shared GTR substitution model;
- five alignment partitions;
- estimated GTR rates and base frequencies;
- one shared Yule tree with an estimated birth rate;
- a Gamma(shape=1, rate=0.001) birth-rate prior;
- a Dirichlet(1,1,1,1) base-frequency prior.

## Current purpose

This is the first example used to establish the five-path directory and
execution contract. Posterior summaries and performance reports are outside
the first implementation.
