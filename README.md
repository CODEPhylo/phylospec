# PhyloSpec

A specification for phylogenetic modeling components and their interfaces.

## Overview

PhyloSpec provides a standardized way to describe phylogenetic modeling components (distributions, functions, and types) that can be shared across different phylogenetic inference engines. Core of PhyloSpec is a modeling language designed to describe phylogenetic models.

PhyloSpec aims to bring the community together by developing shared tooling like the [Bayesian Model Builder](https://github.com/alexeid/bayesian-model-builder), editor extensions, simulators, visualization tools and much more. It simplifies model specification for researchers and improves reproducing and building upon existing results.

A (very) simple PhyloSpec model might look like as follows:

```
Alignment observedAlignment = nexus(
  file="alignment.nexus"
)

QMatrix Q = HKY(
	kappa ~ LogNormal(sdlog=0.5, meanlog=1.0),
	baseFrequencies ~ Dirichlet(alpha=[2.0, 2.0, 2.0, 2.0]),
)
Tree tree ~ Coalescent(
	taxa = observedAlignment.taxa,
	populationSize ~ LogNormal(meanlog=3.0, sdlog=2.0),
)

@observedAs(observedAlignment)
Alignment alignment ~ PhyloCTMC(Q, tree)
```

The specification consists of:

1. **PhyloSpec Specification** - Written language specification
2. **ANTLR Grammar** - Machine-readable grammar for the language
3. **JSON Component Library Format and Core Component Library** - Machine-readable specifications for types, distributions, and functions
4. **Engine Integration Format** - Standardized way for engines to describe their capabilities
5. **Documentation** - Comprehensive guides for types, distributions, functions, and constraints

## Key Features

- **Engine-Agnostic** - Core components work across RevBayes, BEAST 3, and other compliant engines
- **Type-Safe** - Strong typing with constraints prevents invalid models
- **Extensible** - Engines can add custom components while maintaining compatibility
- **Dimension-Aware** - Dynamic sizing based on model structure
- **Machine-Readable** - JSON format enables automated tooling

## First Steps

- Check out the [introduction slides](https://polybox.ethz.ch/index.php/s/Z2TPYFQBFk5qNjP).
- Check out the [VS Code extension demo](https://polybox.ethz.ch/index.php/s/8TSWd7mqLRiEcTJ).
- Check out the [introduction to the PhyloSpec language](docs/language.md).
- Check out the [other documentation](docs).
- Install and try out the [VS Code extension](tools/vscode/README.md) featuring syntax highlighting, type checking, auto-completion, and information-on-hover.

## Repository Structure

```
.
├── core/                          # Core implementations
│   └── java/                      # Java reference implementation
│       ├── src/main/java/         # Type system and annotations
│       │   └── org/phylospec/
│       │       ├── annotations/   # PhyloSpec annotations
│       │       ├── ast/           # Nodes of syntax tree
│       │       ├── components/    # Classes corresponding to components in component libraries
│       │       ├── converters/    # Classes to convert PhyloSpec into other languages (Rev, LPhy, JSON)
│       │       ├── domain/        # Bounded primitive types (PositiveReal, Probability, etc.)
│       │       ├── factory/       # Type factory utilities
│       │       ├── lexer/         # Lexer
│       │       ├── lsp/           # LSP Server
│       │       ├── parser/        # Parser
│       │       ├── typeresolver/  # Type resolver and static type checker
│       │       └── types/         # Complex types (Matrix, Vector, etc.)
│       └── src/test/              # Unit and integration tests
├── tools/                         # Related tools
│   └── vscode/                    # VS Code Extension
├── docs/                          # Documentation
│   ├── language.md                # Written language specification
│   ├── types.md                   # Type system specification
│   ├── distributions.md           # Distribution signatures
│   ├── functions.md               # Function signatures
│   └── constraints.md             # Constraint definitions
├── website/                       # Svelte website for the project
└── schema/                        # JSON schemas and specifications
    ├── component-library.schema.json  # Metaschema for component libraries for validating model libraries
    └── phylospec-core-component-library.json # Core PhyloSpec components
```

## JSON Component Library Format

PhyloSpec uses a standardized JSON format to describe available components. It's used to define the [**core component library**](schema/phylospec-core-component-library.json) and allows engine and package developers to define **additional external component libraries**. A simplified example looks as follows:

```json
{
  "modelLibrary": {
    "name": "PhyloSpec Core",
    "version": "1.3.0",
    "types": [
      {
        "name": "PositiveReal",
        "description": "Positive real number (> 0)",
        "extends": "Real",
        "constraint": "positive"
      }
    ],
    "generators": [
      {
        "name": "Exponential",
        "generatorType": "distribution",
        "generatedType": "PositiveReal",
        "arguments": [
          {
            "name": "rate",
            "type": "PositiveReal",
            "required": true,
            "default": 1.0
          }
        ]
      }
    ]
  }
}
```

## JSON Engine Integration Format

The **engine integration format** allows engines to document engine-specific limitations in a standardized and machine-readable way. The limitations include unsupported core components, arguments which cannot be random variables, and more.

## Java Reference Implementation

The `core/java` directory contains a reference implementation of the PhyloSpec type system:

```java
import org.phylospec.domain.*;
import org.phylospec.types.*;
import static org.phylospec.factory.PhyloSpecTypes.*;

// Create typed values
PositiveReal rate = positiveReal(0.5);
Simplex frequencies = simplex(0.25, 0.25, 0.25, 0.25);

// Use with annotations
@PhyloSpec(name = "MyModel")
public class MyModel {
    @PhyloParam(name = "rate")
    private PositiveReal substitutionRate;
}
```

### Building the Java Library

```bash
cd core/java
mvn clean install
```

## For Engine Developers

To make your engine PhyloSpec-compliant:

1. Create a JSON file describing your engine's components following the schema
2. Include any extensions to the core PhyloSpec types
3. Create a JSON file documenting any restrictions
4. Provide a public URL for the Bayesian Model Builder to load

Example engine-specific component library:

```json
{
  "extends": "https://phylospec.org/core/v1.3.0",
  "modelLibrary": {
    "name": "MyEngine Extensions",
    "engineName": "MyEngine",
    "engineVersion": "2.0",
    "generators": [
      {
        "name": "CustomDistribution",
        "generatorType": "distribution",
        "generatedType": "Real",
        "arguments": []
      }
    ]
  }
}
```

## For User Interface Developers

To make a GUI for PhyloSpec:

1. Create a modular editor based on the core component library and additional external component libraries
2. Implement the engine integration format to respect engine limitations
3. Provide a PhyloSpec output of the constructed model

## Current Status

- ✅ JSON schema for core component library (`schema/component-library.schema.json`)
- ✅ Core PhyloSpec component library (`schema/phylospec-core-component-library.json`)
- ✅ Java type system implementation (`core/java`)
- ✅ Integration with Bayesian Model Builder
- ✅ Parser, Type Checker, and LSP
- 📋 Engine Integration Format (planned)
- 📋 ANTLR grammar (planned)
- 📋 Additional validation tools (planned)

## Documentation

- [Language](docs/language.md) - Detailed language specifications
- [Type System](docs/types.md) - Detailed type specifications
- [Distributions](docs/distributions.md) - Statistical distribution signatures
- [Functions](docs/functions.md) - Function signatures and semantics
- [Constraints](docs/constraints.md) - Constraint system documentation

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Versioning

PhyloSpec follows [Semantic Versioning](https://semver.org/):

- MAJOR: Incompatible specification changes
- MINOR: Backward-compatible additions
- PATCH: Bug fixes and clarifications

Current version: 1.3.0

## Related Projects

- [Bayesian Model Builder](https://github.com/alexeid/bayesian-model-builder) - Web app for constructing phylogenetic models
- [RevBayes](https://revbayes.github.io/) - Bayesian phylogenetic inference engine
- [BEAST 3](https://www.beast2.org/) - Bayesian evolutionary analysis platform

## Citation

If you use PhyloSpec in your research, please cite:

```bibtex
@software{phylospec2025,
  author = {CODEPhylo Working Group},
  title = {PhyloSpec: A Specification for Phylogenetic Modeling Components},
  year = {2025},
  publisher = {GitHub},
  url = {https://github.com/CODEPhylo/phylospec}
}
```

## License

This project is licensed under the [MIT License](LICENSE).

## Contact

- **Issues**: [GitHub Issues](https://github.com/CODEPhylo/phylospec/issues)
- **Discussions**: [GitHub Discussions](https://github.com/CODEPhylo/phylospec/discussions)
