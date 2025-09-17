# PhyloSpec

A specification for phylogenetic modeling components and their interfaces.

## Overview

PhyloSpec provides a standardized way to describe phylogenetic modeling components (distributions, functions, and types) that can be shared across different phylogenetic inference engines. It will enable front-ends such as the [Bayesian Model Builder](https://github.com/alexeid/bayesian-model-builder) web application to construct models that are compatible with multiple engines while maintaining type safety and proper constraints.

The core of PhyloSpec consists of:

1. **ANTLR Grammar** - Defines the syntax for phylogenetic modeling languages
2. **JSON Schema** - Machine-readable specifications for types, distributions, and functions
3. **Model Library Format** - Standardized way for engines to describe their capabilities
4. **Type System** - Well-defined types with constraints for phylogenetic concepts

## Key Features

- **Engine-Agnostic** - Core components work across RevBayes, BEAST 3, and other compliant engines
- **Extensible** - Engines can add custom components while maintaining compatibility
- **Type-Safe** - Strong typing with constraints prevents invalid models
- **Dimension-Aware** - Dynamic sizing based on model structure
- **Machine-Readable** - JSON format enables automated tooling

## Architecture

```
PhyloSpec Core
├── ANTLR Grammar (syntax definition)
└── JSON Specification
    ├── Common distributions
    ├── Common functions
    └── Common types

Bayesian Model Builder (web app)
├── Loads PhyloSpec core
├── Loads engine-specific extensions via URLs
└── Generates engine-compliant models

Phylogenetic Engines
├── RevBayes
│   └── Engine-specific JSON (extensions/restrictions)
├── BEAST 3
│   └── Engine-specific JSON (extensions/restrictions)
└── Other engines...
```

## Proposed Repository Structure

```
.
├── grammar/               # ANTLR grammar files
│   └── PhyloSpec.g4      # Core grammar definition
├── schema/               # JSON schemas
│   ├── model-library.schema.json    # Metaschema for validating model libraries
│   └── phylospec-model-library.json # Core PhyloSpec components
├── docs/                 # Documentation
│   ├── types.md         # Type system specification
│   ├── distributions.md # Distribution signatures
│   └── functions.md     # Function signatures
├── examples/            # Example model specifications
└── tools/              # Validation and conversion utilities
```

## JSON Model Library Format

PhyloSpec uses a JSON format to describe available components. Here's a simplified example:

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

## For Engine Developers

To make your engine PhyloSpec-compliant:

1. Create a JSON file describing your engine's components
2. Include any extensions to the core PhyloSpec types
3. Document any restrictions (e.g., arguments that must be fixed)
4. Provide a public URL for the Bayesian Model Builder to load

Example engine-specific extension:
```json
{
  "extends": "https://phylospec.org/core/v1.3.0",
  "engineName": "MyEngine",
  "engineVersion": "2.0",
  "additions": {
    "generators": [
      {
        "name": "CustomDistribution",
        "generatorType": "distribution",
        "generatedType": "Real"
      }
    ]
  },
  "restrictions": {
    "BirthDeath": {
      "arguments": {
        "conditioning": {
          "mustBeFixed": true
        }
      }
    }
  }
}
```

## Current Implementation Status

- ✅ JSON schema for model libraries
- ✅ Core PhyloSpec types and distributions
- ✅ Dimension expressions for dynamic sizing
- ✅ Integration with Bayesian Model Builder
- 🚧 ANTLR grammar (in progress)
- 🚧 Validation tools (in progress)
- 📋 Additional engine integrations (planned)

## Standardization Decisions

Based on community discussions:
- **Unicode support**: Yes
- **Parameter naming**: Following R conventions (e.g., `meanlog`/`sdlog`)
- **Type constraints**: Supported via JSON schema
- **Vectorization**: Multiple approaches under consideration

## Contributing

We welcome contributions! Areas where help is needed:
1. Expanding the set of common distributions and functions
2. Improving documentation and examples
3. Building validation tools
4. Creating engine-specific extensions

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
- **Working Group**: [CODEPhylo](https://github.com/CODEPhylo)
