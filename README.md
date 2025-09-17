# PhyloSpec

A specification for phylogenetic modeling components and their interfaces.

## Overview

PhyloSpec provides a standardized way to describe phylogenetic modeling components (distributions, functions, and types) that can be shared across different phylogenetic inference engines. It enables front-ends like the [Bayesian Model Builder](https://github.com/alexeid/bayesian-model-builder) web application to construct models that are compatible with multiple engines while maintaining type safety and proper constraints.

The specification consists of:

1. **JSON Schema** - Machine-readable specifications for types, distributions, and functions
2. **Java Reference Implementation** - Core type system with annotations
3. **Model Library Format** - Standardized way for engines to describe their capabilities
4. **Documentation** - Comprehensive guides for types, distributions, functions, and constraints

## Key Features

- **Engine-Agnostic** - Core components work across RevBayes, BEAST 3, and other compliant engines
- **Type-Safe** - Strong typing with constraints prevents invalid models
- **Extensible** - Engines can add custom components while maintaining compatibility
- **Dimension-Aware** - Dynamic sizing based on model structure
- **Machine-Readable** - JSON format enables automated tooling

## Repository Structure

```
.
├── core/                          # Core implementations
│   └── java/                      # Java reference implementation
│       ├── src/main/java/         # Type system and annotations
│       │   └── org/phylospec/
│       │       ├── annotations/   # PhyloSpec annotations
│       │       ├── factory/       # Type factory utilities
│       │       ├── primitives/    # Primitive types (Real, Int, etc.)
│       │       └── types/         # Complex types (Matrix, Vector, etc.)
│       └── src/test/              # Unit tests
├── docs/                          # Documentation
│   ├── types.md                   # Type system specification
│   ├── distributions.md           # Distribution signatures
│   ├── functions.md               # Function signatures
│   └── constraints.md             # Constraint definitions
└── schema/                        # JSON schemas and specifications
    ├── model-library.schema.json  # Metaschema for validating model libraries
    └── phylospec-model-library.json # Core PhyloSpec components
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

## Java Reference Implementation

The `core/java` directory contains a reference implementation of the PhyloSpec type system:

```java
import org.phylospec.primitives.*;
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
3. Document any restrictions (e.g., arguments that must be fixed)
4. Provide a public URL for the Bayesian Model Builder to load

Example engine-specific extension:
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

## Current Status

- ✅ JSON schema for model libraries (`schema/model-library.schema.json`)
- ✅ Core PhyloSpec component library (`schema/phylospec-model-library.json`)
- ✅ Java type system implementation (`core/java`)
- ✅ Integration with Bayesian Model Builder
- 📋 ANTLR grammar (planned)
- 📋 Additional validation tools (planned)

## Documentation

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
