# Introducing PhyloSpec

PhyloSpec consists of four main parts:

1. A **standardized way to describe phylogenetic model components** (distributions, functions, and types).
2. A **core set of model components**.
3. A **modeling language** that allows to combine these components and describe phylogenetic analyses compatible with different engines.
4. A **standardized way to describe inference engine capabilities** (supported components, limitations, extensions).

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

## Join the working group

<a href="mailto:tobia.ochsner@bsse.ethz.ch">Send us an email</a> to join the working group!

Current members:

- Tobia Ochsner, Tim Vaughan, Tanja Stadler (ETH Zürich)
- Alexei Drummond (University of Auckland)
- Sebastian Höhna (LMU Munich)

## Roadmap & Progress

### JSON Component Library Format
**Status:** 🔄 Preliminary

A preliminary JSON schema is available. It will be refined throughout the next few months.

<a href="./specification" target="_blank">→ Learn more about the specification</a>

### Core Component Library
**Status:** 🔄 Preliminary

An early draft of standard types, distributions, and functions is available. This will be refined through community discussion and the PhyloSpec working group to establish common components and best practices.

<a href="./components">→ View the core components</a>

### Modeling Language
**Status:** 🚧 In Progress

A preliminary language specification is available. The exact syntax will be refined throughout the next few months.

<a href="./language">→ Read the language documentation</a>

### Prototypes
**Status:** 🚧 In Progress

Working prototypes include:

- Parser & Type Checker
- LSP & VS Code Extension
- Model Converters (to JSON, to Rev, to LPhy)
- Runner — Execute PhyloSpec models in RevBayes and BEAST 2

<a href="./prototypes">→ Check out the prototypes</a>

### Engine Integration Format
**Status:** 📝 Planned

A standardized format to document engine-specific capabilities and limitations will enable researchers to specify models which will actually run in real inference engines.

### Inference Engine Integration
**Status:** 📝 Planned

Direct integration of PhyloSpec into major phylogenetic inference engines (RevBayes, BEAST 2, and others) will allow users to run PhyloSpec models natively without requiring external converters or runners.