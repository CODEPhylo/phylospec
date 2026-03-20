---
title: "Proposed Improvements"
date: "2026-03-20"
author: "Tobia Ochsner"
---

# Proposed Improvements

This post describes several proposed improvements over *Draft 12.2025* of the language specifications and core component library. The changes take into account feedback from several people, as well as learnings from trying to describe more complex models in PhyloSpec. Lastly, I tried to improve consistency and adherence to [our core principles](./the-way-of-phylospec).

There is a great level of subjectivity to some of these changes. Feel free to provide feedback in the GitHub discussion of this post!

## Improving the Feel of PhyloSpec

With the first batch of changes, I tried to make PhyloSpec feel less like a programming language and more like a natural description of the model.


### Change #1: Clamping Syntax

Previously, we used decorators for clamping (`@observedAs(10)`). However, the dual use of decorators for clamping and engine-specific hints might be confusing. I thus propose a different syntax for clamping:

```phylospec
Alignment alignment ~ PhyloCTMC(
    tree, qMatrix, siteRates
) observed as data
```

Decorators for engine-specific hints make a lot of sense, because you give hints to ("at") a specific engine:

```phylospec
@beast3(someFlag=True) // we tell beast3 what to do here
Tree tree ~ Yule(
    ...
)
```

### Change #2: Indexed Vectorization

Instead of list comprehension, I propose to use the following index notation:

```phylospec
Vector<Real> values = [1, 2, 3]
Real squared[i] = values[i] * values[i] for i in 1:num(values)
Real rate[i] ~ LogNormal(
    logMean=values[i], logSd=0.5
) for i in 1:num(values)
```

This very closely mirrors mathematical syntax, while being slightly more concise than list comprehension.

However, this syntax is less flexible than list comprehension. Notably, it can't be used for inline vector creation:

```phylospec
Alignment alignment = PhyloCTMC(
    tree,
    qMatrix,
    branchRates=[i for i in 1:numBranches(tree)]
)
```

must be written as

```phylospec
Rate branchRates[i] = i for i in 1:numBranches(tree)
Alignment alignment = PhyloCTMC(
    tree,
    qMatrix,
    branchRates
)
```

### Change #3: Function Parameters

The name of a function parameter can be dropped for the first argument and if the variable is called like the parameter:

```phylospec
Alignment data = fromNexus("file.nex", ageParser=parse(...))

Tree tree ~ ...
QMatrix qMatrix = ...

Alignment alignment ~ PhyloCTMC(
    tree, qMatrix
)
```

This greatly reduces verbosity without affecting readability.

### Minor changes

- #4: External namespaces are imported using the `use` keyword to make them feel less like a programming language: `use bdmmprime.BirthDeathMigration`.
- #5: The alias `Age` was added to highlight that we measure time backwards from the present.
- #6: The IO functions `readXYZ` have been renamed to `fromXYZ`.

## Abstraction Level

From [our core principles](./the-way-of-phylospec): *(...) the language should help researchers understand the Bayesian phylogenetic framework by making core concepts explicit (...)*.

The following changes address the abstraction level of the building blocks used to describe models. My goal is to use building blocks that correspond to concepts which might show up in a Bayesian Phylogenetics class or in the methodology section of a paper applying Bayesian phylogenetics.

### Change #7: Clock Models

```phylospec
Vector<Rate> branchRates1 ~ StrictClock(2.0, tree)
Vector<Rate> branchRates2 ~ RelaxedClock(
    Exponential(rate=2.0), tree
)
```

### Change #8: Site Heterogeneity

```phylospec
Vector<Rate> siteRates ~ DiscreteGammaInv(
    shape=2.0, numCategories=4, invariantProportion=0.1, numSites=numSites(alignment)
)
```

Another way to look at these changes is that we put a label on generators, but are explicit in what they generate (a `StrictClock` explicitly generates a vector of branch rates). This is consistent with how we treat substitution models:

```phylospec
QMatrix qMatrix = jc69()
```

## Allowing More Realistic Models

The following changes resulted after I attempted to translate existing BEAST 2 XMLs into PhyloSpec. They are things required to model realistic models in practice.

### Change #9: Blocks

Ideally, researchers would want to put their PhyloSpec model into a figure of their paper, because it is the most concise way to summarize their analysis.

However, in practice, some level of data wrangling or implementation details (like the chain length or versions) are unavoidable. Thus, we follow the path of LPhy, Stan and BUGS and allow different blocks:

```phylospec
data {
    // some data processing
}

model {
    // the model description
}

mcmc {
    // some general technicalities like chain length and output files
}

revbayes {
    // some engine specific block
}

```

All blocks are optional. Statements in the `data` and `model` can also be put outside of any block.

The `data` block cannot contain any random variables drawn from distributions. No statement in the `data` can reference a variable defined in another block. Statements in the `model` block can reference variables defined in the `data` block.

The statements in all blocks are parsed by the PhyloSpec parser and must adhere to the grammar. However, statements in engine-specific blocks are neither resolved nor type-checked and are directly passed to the engine.

An engine should choose reasonable defaults if no `mcmc` or engine-specific block is given. There will be a concrete list of allowed variables in the `mcmc` block (tbd).

### Change #10: String Interpolation

We can inject variables into string literals using string interpolation:

```phylospec
String seed = env(seed)
String fileName = "analysis_${seed}.nex"
```

Only variable names can be used within the curly brackets. If a string literal should actually contain `${`, we can escape it using `\`:

```phylospec
String fileName = "analysis_\\${seed}.nex" // "analysis_\\${seed}.nex"
```

### Change #11: Extracting Information Out of Taxa Names

We introduce the `parse` function which describes a way to extract information out of a string. This can be used to extract information out of taxa names:

```phylospec
Alignment alignment = fromNexus(
    "file.nex",
    speciesName=parse(delimiter="_", part=1),
    age=parse(delimiter="_", part=3),
)
Alignment traits = traitsFromTaxa(
    taxa=taxa(alignment),
    trait=parse(regex="^[^_]+_[^_]+_([^_]+)")
)
```

Conceptually, the `traits` alignment inherits the taxon ages and species names from the `alignment` alignment. This works because they are tied to the taxa itself:

```phylospec
Age age = age(taxa(alignment)[1])
String speciesName = species(taxa(alignment)[1])
```

### Change #12: Truncated Distributions

We can truncate existing scalar distributions:

```phylospec
PositiveReal x ~ Truncated(
    Normal(mean=0, sd=1), lower=2.0
)

Real y ~ Truncated(
    Normal(mean=0, sd=1), upper=2.0
)

NonNegativeReal z ~ Truncated(
    Normal(mean=0, sd=1), lower=0.0, upper=10.0
)
```

Type parameters on the `lower` and `upper` arguments provide type safety up to the discrete set of scalar types.

### Change #13: Time-varying Values

I propose native support for time-varying values like rates or population sizes:

```phylospec
Varying<Rate> rate1 = piecewise(
    pieces=[0.01, 0.02, 0.03],
    changeAges=[5.0, 10.0]
)
Varying<Rate> rate2 = piecewise(
    pieces=[
        constant(2.0),
        exponentialGrowth(startValue=10, growthRate=2.0),
        logisticGrowth(inflectionAge=2, carryingCapacity=10, growthRate=2.0),
    ],
    changeAges=[5.0, 10.0]
)

Varying<Rate> rate3 ~ Piecewise(
    LogNormal(logMean=1.0, logSd=1.0), changeAges=[5.0, 10.0]
)
Varying<Rate> rate4 ~ Piecewise(
    pieces=[
        LogNormal(logMean=1.0, logSd=1.0),
        LogNormal(logMean=2.0, logSd=1.0),
        LogNormal(logMean=3.0, logSd=1.0)
    ],
    changeAges=[5.0, 10.0]
)
```

### Minor Changes

- #14: The `env` function allows access to environment variables.
- #15: The `fromCSV` function and the `Map<K, V>` type have been added.
- #16: The `Binomial` and `Cauchy` distributions have been added.
- #17: The functions `mrca` and `age` have been added to retrieve clade and taxon ages.
- #18: The `mk` substitution model now has an optional parameter for the expected rate.

## Examples

The following model leverages some of the proposed changes:

```phylospec
data {
    String seed = env("SEED")
    Alignment molecularData = fromNexus(
        "alignment_${seed}.nex", age=parse(delimiter="_", part=1)
    )
    Alignment traitData = traitsFromTaxa(
        taxa(molecularData), trait=parse(delimiter="_", part=2)
    )
}

model {
    Varying<Rate> branchRate ~ Piecewise(
        Exponential(rate=1.0), changeAges=[5.0, 10.0]
    )
    Tree tree ~ Yule(
        birthRate, taxa=taxa(molecularData)
    )

    QMatrix molecularQ = hky(
        kappa~LogNormal(logMean=0.1, logSd=2.0),
        baseFrequencies~Dirichlet([1.0, 1.0, 1.0, 1.0])
    )
    QMatrix traitQ = mk(
        rate~LogNormal(logMean=0.1, logSd=2.0)
    )

    Vector<Rate> siteRates ~ DiscreteGammaInv(
        shape=1.0, 
        numCategories=4, 
        invariantProportion=0.1, 
        numSites=numSites(molecularData)
    )

    Vector<Rate> branchRates ~ RelaxedClock(
        LogNormal(logMean=1.0, logSd=2.0),
        numBranches=numBranches(tree)
    )

    Alignment molecular ~ PhyloCTMC(
        tree, qMatrix=molecularQ, siteRates, branchRates
    ) observed as molecularData
    Alignment traits ~ PhyloCTMC(
        tree, qMatrix=traitQ, branchRates
    ) observed as traitData
}

mcmc {
    Integer chainLength = 1e8
    String logFile = "output.log"
    String treesFile = "output.trees"
}
```
