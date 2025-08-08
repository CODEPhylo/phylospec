# PhyloSpec

A reference specification for phylogenetic modeling languages.

## Overview

PhyloSpec provides a common type system and formal signature definitions for phylogenetic modeling languages. It enables semantic interoperability between different syntactic implementations, allowing models to be translated and shared across different platforms and tools.

The core of PhyloSpec consists of:

1. **Type System** - Well-defined types for phylogenetic concepts
2. **Distribution Signatures** - Precise parameter definitions for statistical distributions
3. **Function Signatures** - Standardized interfaces for common functions
4. **Constraint System** - Uniform way to express model constraints

## Key Features

- **Language-Agnostic** - PhyloSpec focuses on semantics, not syntax
- **Machine-Readable** - Specifications are available in JSON for tool integration
- **Human-Readable** - Comprehensive documentation for implementers
- **Reference Implementation** - Core Java library with type system and annotations
- **Validation Support** - Runtime type validation and constraint checking

## Repository Structure

```
.
├── core/                   # Core implementations
│   └── java/              # Java reference implementation
│       ├── src/           # Source code (types, annotations, factory)
│       └── pom.xml        # Maven build configuration
├── docs/                   # Human-readable documentation
│   ├── types.md           # Type system specification
│   ├── distributions.md   # Distribution signatures
│   ├── functions.md       # Function signatures
│   └── constraints.md     # Constraint definitions
├── schema/                 # Machine-readable JSON schemas
│   ├── types.json         # Type definitions
│   ├── distributions.json # Distribution definitions
│   ├── functions.json     # Function definitions
│   └── constraints.json   # Constraint definitions
├── examples/               # Example models in different languages
│   ├── codephy/          # Codephy examples
│   ├── modelphy/         # ModelPhy examples
│   └── stackphy/         # StackPhy examples
├── adapters/              # Adapters for external tools (future)
├── validators/            # Validation tools (future)
└── tools/                 # Utilities (future)
```

## Getting Started

### For Model Authors

If you're building phylogenetic models, PhyloSpec provides:
- A consistent type system across modeling languages
- Standard distribution and function signatures
- Type safety and validation

Check the [type system documentation](docs/types.md) and browse [examples](examples/) in different languages.

### For Language Implementers

If you're developing a phylogenetic modeling language:
1. Review the [type specification](docs/types.md)
2. Examine the [Java reference implementation](core/java/)
3. Use the [JSON schemas](schema/) for validation
4. Follow the naming conventions and constraint rules

### Using the Java Implementation

```java
import org.phylospec.types.*;
import static org.phylospec.factory.PhyloSpecTypes.*;

// Create typed values
PositiveReal rate = positiveReal(0.5);
Simplex frequencies = simplex(0.25, 0.25, 0.25, 0.25);

// Build a Q-matrix
QMatrix q = qMatrix(new double[][]{
    {-1.0,  0.5,  0.5},
    { 0.3, -0.6,  0.3},
    { 0.2,  0.4, -0.6}
});

// Use with annotations
@PhyloSpec(name = "MyModel")
public class MyModel {
    @PhyloParam(name = "rate")
    private PositiveReal substitutionRate;
}
```

See the [Java implementation README](core/java/README.md) for detailed usage.

## Implementations

### Core Implementation
- **Java** - Reference implementation in `core/java/` with full type system and annotations

### Language Bindings
PhyloSpec is implemented by:
- [Codephy](https://github.com/CODEPhylo/codephy) - JSON-based phylogenetic modeling
- [StackPhy](https://github.com/CODEPhylo/stackphy) - Stack-based phylogenetic modeling
- [ModelPhy](https://github.com/CODEPhylo/modelphy) - Declaration-based phylogenetic modeling

## Building from Source

### Java Core Library

```bash
cd core/java
mvn clean install
```

This builds the type system, runs tests, and installs to your local Maven repository.

## Contributing

We welcome contributions! Please:
1. Follow the established naming conventions
2. Add tests for new functionality
3. Update documentation accordingly
4. Submit pull requests for review

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

## Versioning

PhyloSpec follows [Semantic Versioning](https://semver.org/):
- MAJOR version for incompatible specification changes
- MINOR version for backward-compatible additions
- PATCH version for bug fixes and clarifications

Current version: 1.0.0-SNAPSHOT

## License

This project is licensed under the [MIT License](LICENSE).

## Citation

If you use PhyloSpec in your research, please cite:

```bibtex
@software{phylospec2025,
  author = {CODEPhylo steering group},
  title = {PhyloSpec: A Reference Specification for Phylogenetic Modeling Languages},
  year = {2025},
  publisher = {GitHub},
  url = {https://github.com/CODEPhylo/phylospec}
}
```

## Contact

- **Issues**: [GitHub Issues](https://github.com/CODEPhylo/phylospec/issues)
- **Discussions**: [GitHub Discussions](https://github.com/CODEPhylo/phylospec/discussions)
- **Website**: [CODEPhylo](https://github.com/CODEPhylo)