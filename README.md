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
- **Validation Tools** - Utilities to check compliance with the specification
- **Reference Implementations** - Adapters for Codephy, StackPhy, and ModelPhy

## Repository Structure

- `docs/` - Human-readable specification and documentation
- `schema/` - Machine-readable JSON schema definitions
- `validators/` - Tools to validate implementation compliance
- `adapters/` - Reference implementations for various languages
- `test/` - Test suite with example models
- `tools/` - Utilities for working with PhyloSpec

## Getting Started

### For Model Authors

If you're building phylogenetic models, PhyloSpec provides a consistent reference for types, distributions, and functions across different modeling languages. Check the [documentation](docs/spec/index.md) to understand the concepts and see [examples](docs/examples/) of models written in different languages.

### For Language Implementers

If you're developing a phylogenetic modeling language, PhyloSpec offers a reference to ensure semantic compatibility with other languages. Start with the [specification](docs/spec/index.md) and use the [schema files](schema/) to validate your implementation.

## Implementations

PhyloSpec is currently implemented in:

- [Codephy](https://github.com/CODEPhylo/codephy) - JSON-based phylogenetic modeling
- [StackPhy](https://github.com/CODEPhylo/stackphy) - Stack-based phylogenetic modeling
- [ModelPhy](https://github.com/CODEPhylo/modelphy) - Declaration-based phylogenetic modeling

## Contributing

We welcome contributions to PhyloSpec! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute.

## Versioning

PhyloSpec follows [Semantic Versioning](https://semver.org/):

- MAJOR version for incompatible changes to the specification
- MINOR version for backward-compatible additions
- PATCH version for clarifications and documentation improvements

## License

This project is licensed under the [MIT License](LICENSE).

## Citation

If you use PhyloSpec in your research, please cite:

```
@misc{PhyloSpec2025,
  author = {CODEPhylo steering group},
  title = {PhyloSpec: A Reference Specification for Phylogenetic Modeling Languages},
  year = {2025},
  publisher = {GitHub},
  url = {https://github.com/CODEPhylo/phylospec}
}
```

## Contact

For questions about PhyloSpec, please [open an issue](https://github.com/CODEPhylo/phylospec/issues) or start a [discussion](https://github.com/CODEPhylo/phylospec/discussions).
