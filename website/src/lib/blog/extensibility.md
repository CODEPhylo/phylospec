---
title: "On Ownership and Extensibility"
date: "2026-08-06"
author: "Tobia Ochsner"
---

<script>
  import { Mermaid } from '@friendofsvelte/mermaid';

  const shapeBasedMatchingType = `flowchart LR
    subgraph A["Component Library A"]
        direction TB
        A2["v2: readNexus(String file) → Alignment&lt;Character&gt;"]
        A1["v1: readNexus(String file) → Alignment"]
    end

    subgraph B["Component Library B"]
        direction TB
        B4["v4: Alignment&lt;T&gt;"]
        B3["v3: Alignment&lt;T&gt;"]
        B2["v2: Alignment"]
        B1["v1: Alignment not defined"]
    end

    A2 --- B4
    A2 --- B3
    A1 --- B2
    `

  const engineSupport = `graph LR
    A["PhyloSpec Model"]
    B["BEAST 2 v2.7+ and BEASTLabs v2.7.4+ and BDMM v1.0.3+"]
    C["BEAST X v10.3+"]
    D["RevBayes: not supported"]

    A --> B
    A --> C
    A --> D`

  const ownershipWorkflow = `flowchart LR
    M["PhyloSpec Model"]
    T["Tooling<br/>(GUI, IDE, CLI)"]
    E["Engine"]
    R["Repositories<br/>(component libraries, engine specs)"]
    O["Output"]

    M --> T
    M --> E
    T -->|"validates against"| R
    T -->|"gives engine advice"| R
    E -.->|"never consults"| R
    E -->|"runs, fails on incompatibility"| O`

  const versionDecoupling = `flowchart TB
    subgraph SW["BEAST 2"]
        direction LR
        S1["2.7.4"] --> S2["2.7.5"] --> S3["2.7.6"]
    end
    subgraph ES["beast2-phylospec"]
        direction LR
        E1["2.7.4"] --> E2["2.7.5"] --> E3["2.7.6"]
    end
    subgraph CL["core-components"]
        direction LR
        C1["1.0.0"] --> C2["1.1.0"] --> C3["1.2.0"]
    end

    S1 -.->|"1:1"| E1
    S2 -.->|"1:1"| E2
    S3 -.->|"1:1"| E3`
</script>

# On Ownership and Extensibility

PhyloSpec models exist in an ecosystem. They use components from component libraries maintained by different groups, and get executed by engines that release on their own schedule. As time passes, questions around provenance, versioning, and dependency resolution become relevant. This post proposes ways to answer these—starting with where components and engine specifications live, who depends on what, and why we can mostly avoid relying on version numbers.

## Repositories

PhyloSpec Repositories host component libraries and engine specifications. The components in a repository are unique up to the namespace and name.

There is a <a href="https://github.com/tochsner/phylospec-repository" target="_blank">central PhyloSpec repository</a>. However, third parties can host their own repository as long as it follows the same format. This is also useful for development. Components are namespaced by the repository URL.

**Component libraries** contain components (generators and types) that make up a phylogenetic analysis. They are hand-authored. An example is the core component library.

**Engine specifications** describe the components implemented by an engine, as well as the limitations of this implementation. They also provide instructions on how to install an engine. They should be auto-generated from the engine code. Examples are the engine specifications for BEAST 2 or RevBayes. In the case of the BEAST 2 ecosystem, we also treat packages as dedicated engines, as they implement their own set of components.

A repository contains every version of each component library and engine specification side by side.

## Workflow and Ownership

There are two sides that make up a PhyloSpec workflow:

**Tooling** helps users build a valid PhyloSpec model. It includes GUIs, IDE integrations, and the PhyloSpec CLI. Tooling uses the component libraries to validate a given model. Further, it leverages engine specifications to give advice on model compatibility and on how to install engines compatible with a model.

**Engines** run a PhyloSpec model using an existing MCMC machinery. An engine installed locally on a computer acts like the runtime of a programming language. You pass it a PhyloSpec model and it tries to execute it. The engine itself does not consult the PhyloSpec repositories to see whether it understands the model; it simply runs it and fails whenever there is an incompatibility.

An engine can implement only a subset of a component library; an example of this is the BEAST X engine. An engine can also implement components from multiple libraries, as might be the case for a BEAST 2 package.

Sometimes, multiple engines work together to run a model. An example is a model which requires BEAST 2 and a specific package.

<Mermaid string={ownershipWorkflow} class="mermaid" />

## Versioning

We attach versions to different things:

- A PhyloSpec repository is versioned implicitly by Git.
- Each component library has its own version.
- Each engine specification inherits its version from the piece of software it describes.

<Mermaid string={versionDecoupling} class="mermaid" />

Versioning generally addresses two problems:

### Reproducibility and Provenance

Versioning improves reproducibility by making sure the same things are used when replicating a task in the future.

For provenance, only the versions of the engines used to execute the model ultimately matter. The versions of components and engine specifications used by the user tooling do not affect the actual analysis. This is why we propose that engines create a small artifact file logging the specific software versions used when running an analysis:

```json
// model.receipt.json
{
  "modelName": "model.phylospec",
  "modelHash": "91de323fa50d8ae9d3d430976030d3a",
  "inputHashes": {
    "alignment.nex": "91de323fa50d8ae9d3d43097630d32a"
  },
  "outputHashes": {
    "model.trees": "91de323fa50d8ae9d3d43097630d32a",
    "model.log": "91de323fa50d8ae9d3d43097600d32a"
  },
  "engines": {
      "beast2": "2.8.2",
      "bdmm": "1.0.0"
  }
}
```

An engine can then warn if an analysis is reproduced with different engine versions.

The versions of components and engine specifications affect the behavior of the GUIs and IDE integrations. Thus, it can be useful to pin the repository version used to create the model—then, tooling will behave consistently even once component definitions have moved on. We propose to use an optional manifest file for that purpose (see below).

### Compatibility

Versioning also allows us to resolve compatible combinations whenever there are dependencies amongst different things. To model dependencies, we propose not to use versions at all. Instead, we introduce a concept called shape-based dependencies.

There are three types of dependencies:

1. A component library A can require another library B, because it makes use of a type definition in B.
2. An engine implements a component in a component library.
3. An engine can require another engine.

We use shape-based dependencies for the first two types. In contrast, we specify unversioned dependencies among engines in the engine specifications. We use unversioned dependencies because package resolution and versioning are already managed by the different package managers.

#### Shape-Based Dependencies

Components can ultimately be reduced down to their shape (type parameters for types; arguments, input and return types for generators) and the semantic meaning given by the name. We leverage this by introducing **shape-based dependencies**. What does this mean?

A component library A depends on component library B if it uses types defined in B. With version-based dependencies, we would explicitly specify the versions of B that are compatible with A. With shape-based dependencies, we say A is compatible with B if the types used in A have the same shape as their definitions in B.

The following diagram shows an example. The compatible versions are connected:

<Mermaid string={shapeBasedMatchingType} class="mermaid" />

Shape-based dependencies simplify adding new components, as no explicit dependency versions have to be given.

Engines can specify the supported components using shape-based dependencies as well. The engine specification files document the shape of the supported generators instead of component library version ranges. Here, the shape includes the generator name, return type, and argument names and types. This is more robust and less prone to human error compared to hand-authored version numbers, especially since the shapes can be auto-generated based on the engine code.

Overall, shape-based dependencies can be applied because components are only about semantic concepts. They are advantageous over versioning-based dependencies as they require less human input and are more robust when component libraries independently evolve over time.

### Dependency Resolution

Equipped with shape-based dependencies, we can now talk about dependency resolution. There are two cases where we need to resolve dependencies:

#### Resolution of Compatible Component Libraries

Given a PhyloSpec model and its imported components and a repository, we need to find the most recent compatible combination of libraries covering all components.

A component library A is compatible with another CL B if, for all types defined in A or B, all type usages match up with their definitions.

The resolution can be formulated as a custom backtracking algorithm or as a SAT problem. To simplify things for tooling, we can precompute the compatibilities at the repository level using CI/CD.

#### Resolution of Compatible Engines

Given a PhyloSpec model, we want to figure out which engine combinations can run the model.

As an input for this, we have the generator shapes used in the model, the supported generator shapes for every engine version, and the (non-versioned) dependencies among engines. Resolution now boils down to finding the compatible engine combinations that together implement all necessary component shapes:

<Mermaid string={engineSupport} class="mermaid" />

Note that the version ranges are not declared engine dependencies; they are the versions of each engine whose supported shapes still cover what the model needs. The engines’ native package managers remain responsible for resolving more fine-grained dependencies and determining whether these engines can be installed together.

One can use a custom backtracking algorithm or a more general SAT solver to solve this task.

## Manifest File

Optionally, a JSON manifest file accompanies a PhyloSpec script. It can contain metadata in a structured form to enable model repositories. Furthermore, you can tell the tooling which repository location or version to use. A complete example looks as follows:

```json
// model.manifest.json
{
  "phylospecVersion": "1.0.0",

  "model": {
    "title": "HKY model",
    "description": "A phylogenetic model using HKY substitution model to analyze primate mtDNA",
    "contributors": [
      {
        "identifier": "https://orcid.org/0000-0002-1825-0097",
        "givenNames": "Jane",
        "familyNames": "Smith",
        "email": "jsmith@example.edu",
        "affiliation": {
          "type": "Organization",
          "name": "ETH Zürich"
        }
      }
    ],
    "preferredCitation": {
      "doi": "10.1093/sysbio/syy032",
      "text": "Drummond & Bouckaert (2018). Bayesian evolutionary analysis with BEAST."
    },
    "license": "CC-BY-4.0"
  },

  "repositories": [
    {
      "url": "https://github.com/tochsner/phylospec-components",
      "commit": "92b1c266ced0a319f290f6044f8f02b446371638"
    }
  ]
}
```

## Summary

- Repositories host hand-authored component libraries and auto-generated engine specifications.
- Component libraries and engine specifications are used exclusively by tooling; engines simply try to run a given model.
- Versions only matter for two purposes: provenance by creating a run receipt and keeping tooling's behavior stable by pinning a repository in the manifest.
- Compatibility itself (whether a model can run against a given component library or engine) is decided by shape. This lets libraries and engines keep evolving independently.
- We introduce two small files: `model.manifest.json` with metadata on the model and the repository version used to design the analysis, and `model.receipt.json` with the engine versions used to execute a run.
