# PhyloSpec

A specification for phylogenetic modeling components and their interfaces.

## Overview

PhyloSpec provides a standardized way to describe phylogenetic modeling components (distributions, functions, and types) that can be shared across different phylogenetic inference engines. Core of PhyloSpec is a modeling language designed to describe phylogenetic models.

## First Steps

- Check out our [website](https://codephylo.github.io/phylospec).
- Check out the [PRs](https://github.com/CODEPhylo/phylospec/pulls?q=is%3Apr+) and [our Blog](https://codephylo.github.io/phylospec/blog) for the latest progress.
- Check out the [other documentation](docs).
- Check out the [VS Code extension demo](https://polybox.ethz.ch/index.php/s/8TSWd7mqLRiEcTJ).

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
│       │       ├── errors/        # Error types and reporting
│       │       ├── factory/       # Type factory utilities
│       │       ├── lexer/         # Lexer
│       │       ├── lsp/           # LSP Server
│       │       ├── parser/        # Parser
│       │       ├── typeresolver/  # Type resolver and static type checker
│       │       └── types/         # Complex types (Matrix, Vector, etc.)
│       └── src/test/              # Unit and integration tests
├── integrations/                  # Engine-specific integrations
│   └── beast3/java/               # BEAST 3 integration
├── tools/                         # Related tools
│   └── vscode/                    # VS Code Extension
├── docs/                          # Documentation
│   ├── language.md                # Written language specification
│   ├── types.md                   # Type system specification
│   ├── distributions.md           # Distribution signatures
│   ├── functions.md               # Function signatures
│   └── errors.md                  # Error definitions
├── website/                       # Svelte website for the project
└── schema/                        # JSON schemas and specifications
    └── component-library.schema.json  # Metaschema for component libraries
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

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
