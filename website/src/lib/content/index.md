# Introducing PhyloSpec

PhyloSpec has three main parts:

1. A **specification for describing phylogenetic model components** (distributions, functions, and types).
2. A **core set of standardized components**.
3. A **modeling language** that allows to combine these components and describe phylogenetic analyses compatible with different engines.

## Core goals

### Bringing the community together

PhyloSpec opens up a conversation about standard model components, common assumptions, and best practices in the field of phylogenetics.

We aim to have regular meetings and workshops.

### Ecosystem of tools

A shared modeling language allows to break the existing barriers between ecosystems, such that we can build and build upon shared tools. This decreases duplicate development efforts.

Examples of tools include GUIs, IDE extensions, MCPs, automatic model selectors, model visualization, simulators, model validation, benchmark suites, or code-gen libraries.

### Simplify research

Researches benefit from easier access to the existing inference engines without having to learn all the intricacies of the  different ecosystems.

Educators can teach unified workshops using community-built teaching material.

## Roadmap & Progress

The **Component Specification** is partially completed.

The **Modeling Language Specification** is partially completed. There are working prototypes of the following tools:

- model parser (<a href="https://github.com/CODEPhylo/phylospec/pull/9" target="_blank">PR</a>) and type checker (<a href="https://github.com/CODEPhylo/phylospec/pull/11" target="_blank">PR</a>)
- runner to run PhyloSpec models in RevBayes and BEAST 2  (<a href="https://github.com/tochsner/phylorun/tree/main" target="_blank">check it out</a>)
- LSP (<a href="https://github.com/CODEPhylo/phylospec/pull/12" target="_blank">PR</a>) and VS Code extension (<a href="https://github.com/CODEPhylo/phylospec/tree/main/tools/vscode" target="_blank">check it out</a>)
- PhyloSpec to JSON converter (<a href="https://github.com/CODEPhylo/phylospec/pull/17" target="_blank">PR</a>)
- PhyloSpec to Rev converter (<a href="https://github.com/CODEPhylo/phylospec/pull/16" target="_blank">PR</a>)
- PhyloSpec to LPhy converter (<a href="https://github.com/CODEPhylo/phylospec/pull/14" target="_blank">PR</a>)

The **Core Components** will need to be specified by a working group in the next few months.

Afterwards, the PhyloSpec can be **integrated into the inference engines**.