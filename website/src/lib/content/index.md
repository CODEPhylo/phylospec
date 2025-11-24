# Introducing PhyloSpec

PhyloSpec consists of four main parts:

1. A **standardized way to describe phylogenetic model components** (distributions, functions, and types).
2. A **core set of model components**.
3. A **modeling language** that allows us to combine these components and describe phylogenetic analyses compatible with different engines.
4. A **standardized way to describe inference engine capabilities** (supported components, limitations, extensions).

## Core goals

### Bringing the community together

PhyloSpec opens up a conversation about standard model components, common assumptions, and best practices in the field of phylogenetics.

We aim to have regular meetings and workshops.

### Ecosystem of tools

A shared modeling language allows us to break the existing barriers between ecosystems, such that we can build upon shared tools. This decreases duplicate development efforts.

Examples of tools include GUIs, IDE extensions, MCPs, automatic model selectors, model visualization, simulators, model validation, benchmark suites, or code-gen libraries.

### Simplify research

Researchers benefit from easier access to the existing inference engines without having to learn all the intricacies of the different ecosystems.

Educators can teach unified workshops using community-built teaching material.

## Join the working group

<a href="mailto:tobia.ochsner@bsse.ethz.ch">Send us an email</a> to join the working group!

Current members:

- Tobia Ochsner, Tim Vaughan, Tanja Stadler (ETH Zürich)
- Alexei Drummond, Walter Xie (University of Auckland)
- Sebastian Höhna (LMU Munich)

## Roadmap & Progress

**JSON Component Library Format** — 🔄 Preliminary

A preliminary JSON schema is available. It will be refined throughout the next few months. <a href="./specification" target="_blank">→ Learn more</a>

**Core Component Library** — 🔄 Preliminary

An early draft of standard types, distributions, and functions is available. This will be refined through community discussion and the PhyloSpec working group to establish common components and best practices. <a href="./components">→ View components</a>

**Modeling Language** — 🚧 In Progress

A preliminary language specification is available. The exact syntax will be refined throughout the next few months. <a href="./language">→ Read documentation</a>

**Prototypes** — 🚧 In Progress

Working prototypes include: Parser & Type Checker, LSP & VS Code Extension, Model Converters (to JSON, to Rev, to LPhy), and Runner (Execute PhyloSpec models in RevBayes and BEAST 2). <a href="./prototypes">→ Check out prototypes</a>

**Engine Integration Format** — 📝 Planned

A standardized format to document engine-specific capabilities and limitations.

**Inference Engine Integration** — 📝 Planned

Direct integration of PhyloSpec into major phylogenetic inference engines (RevBayes, BEAST 2, and others).