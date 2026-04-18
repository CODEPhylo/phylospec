---
title: "A First Look at the BEAST 2.8 Parser"
date: "2026-04-16"
author: "Tobia Ochsner"
---

# A First Look at the BEAST 2.8 Parser

I've finalized the first version of a proper PhyloSpec parser for BEAST 2.8 🎉. In this post, we look at the main features parser and at what is yet to come.

The parser takes a PhyloSpec model as an input. It then runs the static type checker and linter to detect common errors. If the model is valid, it builds up a BEAST 2 analysis before BEAST takes over and performs the MCMC analysis.

An example of a model which can be run by the parser is the following:

```phylospec
Alignment data = fromNexus("primate-mtDNA.nex")
Alignment filtered = subset(
    alignment=data,
    start=10,
    end=898
)

Tree tree ~ Yule(
    birthRate~LogNormal(mean=1.0, logSd=2.0),
    taxa=taxa(filtered)
)
QMatrix qMatrix = hky(
    kappa~LogNormal(mean=1, logSd=1.0),
    baseFrequencies=[0.25, 0.25, 0.25, 0.25]
)
Vector<Rate> branchRates ~ StrictClock(
    clockRate~LogNormal(mean=0.1, logSd=2.0),
    tree,
)
Vector<Rate> siteRates ~ DiscreteGammaInv(
    shape~LogNormal(mean=0.1, logSd=2.0),
    numCategories=4,
    invariantProportion=0.1,
    numSites=numSites(filtered)
)

Alignment alignment ~ PhyloCTMC(
    tree, qMatrix, branchRates, siteRates
) observed as filtered

Age root = rootAge(tree) observed between [0.01, 3.0]

mcmc {
    Integer chainLength = 100000
    Logger treeLogger = treeLogger(
        file="trees.trees", logEvery=1000
    )
}
```

## Supported PhyloSpec Features

Right now, 48 out of 76 core components are at least partially supported by the parser (check out the [current status](../beast28-support) for more details). Most of the missing components (like `mk` or `PhyloOU`) are not implemented in beast-core and only work once we add support for external BEAST packages.

Some components are only partially supported. The `exp(x)` function, for instance, can only be used when `x` is a non-stochastic expression or for logging purposes. This is simply because there is no BEAST `StateNode` implementing the exponential function.

On the language side, indexed statements (like Real x[i] = f(i) for i in 1:10), matrix literals, and index accessors are not yet handled.

## Flexibility

Internally, the parser works using a flexible pattern-matching framwork. This means that the following snippets are all equivalent and produce the same BEAST objects:

```phylospec
Alignment alignment ~ PhyloCTMC(
    tree, 
    qMatrix,
    branchRates~StrictClock(
        clockRate~LogNormal(mean=1.0, logSd=0.5), tree
    )
) observed as data
```

```phylospec
Rate clockRate ~ LogNormal(mean=1.0, logSd=0.5)
Vector<Rate> branchRates ~ StrictClock(clockRate, tree)
Alignment alignment ~ PhyloCTMC(
    tree, qMatrix, branchRates
) observed as data
```

```phylospec
Rate clockRate ~ LogNormal(logMean=log(1.0), logSd=0.5)
Rate branchRates[i] = clockRate for i in 1:numBranches(tree)
Alignment alignment ~ PhyloCTMC(
    tree, qMatrix, branchRates
) observed as data
```

## Loggers

By default, a screen logger, a file logger, and a tree logger are created. Every variable and every inline drawn random variable is logged:

```phylospec
Rate birthRate ~ LogNormal(logMean=1.0, logSd=0.5)
Real logBirthRate = log(birthRate)
Tree tree ~ Yule(
    birthRate, tree
)
Real halfRootAge = rootAge(tree) / 2

// birthRate, logBirthRate, tree, and halfRootAge are logged
```

One can use the `mcmc` block to customize the loggers used:

```phylospec
mcmc {
    Logger screenLogger = screenLogger(
        logEvery=100
    )
    Logger fileLogger = fileLogger(
        file="log.log", logEvery=1000, parameters=[birthRate]
    )
    Logger treeLogger = treeLogger(
        file="trees.trees", logEvery=10000
    )
}

```

## Operator Selection

Currently, the operators are automatically set based on the types of random variables. There are also some rules implemented for operators operating on multiple elements. One example is an `UpDownOperator` on the tree and clock rate.

In a coming version, it will be possible to control the operators used in a `beast28` block of a PhyloSpec model.

## Errors

I focused on really helpful error messages that give useful hints on how to solve the problem. Some example error messages are the following:

_Wrong argument names:_

<img src="/errors1.png" class="w-10/10" alt="Screenshot of error message examples" />

_Invalid values:_

<img src="/errors2.png" class="w-7/10" alt="Screenshot of error message examples" />

_Unsupported statements:_

<img src="/errors3.png" class="w-10/10" alt="Screenshot of error message examples" />

_Instantiation errors:_

<img src="/errors4.png" class="w-10/10" alt="Screenshot of error message examples" />

Here, `invalid.newick` does not contain a valid tree. Whenever an error is raised by BEAST itself, the causing PhyloSpec lines are highlighted, but the underlying error is still reported back.

As a side effect, good errors will also improve the experience when building up models using agents.

## Implementation


I will describe the algorithm used by the parser in a follow-up post. However, the parser is based on over 70 small Java classes called _tiles_. Every tile describes how a part of a PhyloSpec model is converted into BEAST objects.

As an example, there are two tiles handling the `exp` function. The first uses the `RPNCalculator` and is relevant for logged objects. The second tile is more versatile and can handle any non-stochastic input. The Java class of the latter looks as follows:

```java
public class ExpTile extends GeneratorTile<RealScalarParam<PositiveReal>> {
    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>> xInput = new GeneratorTileInput<>(
            "x", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public RealScalarParam<PositiveReal> applyTile(BEASTState beastState) {
        RealScalarParam<? extends Real> variable = this.xInput.apply(beastState);
        return new RealScalarParam<>(Math.exp(variable.get()), PositiveReal.INSTANCE);
    }
}
```

The parsing algorithm automatically chooses the right tiles used to build up the BEAST objects.

## Next Steps

The most impactful near-term addition is support for external BEAST packages, which would immediately unlock components like `SkylineCoalescent`, `FossilizedBirthDeath`, and `mk`. After that, the plan is to add a `beast28` block to PhyloSpec models so users can configure operators directly, and to extend the language support to cover indexed statements and matrix literals. On the testing side, a suite that validates the constructed BEAST DAG against hand-crafted XMLs will be essential before the parser can be considered production-ready.
