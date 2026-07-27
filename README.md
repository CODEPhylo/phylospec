# PhyloSpec

A specification for phylogenetic modeling components and their interfaces.

## Overview

PhyloSpec provides a standardized way to describe phylogenetic modeling components (distributions, functions, and types) that can be shared across different phylogenetic inference engines. Core of PhyloSpec is a modeling language designed to describe phylogenetic models.

## First Steps

- Check out our [website](https://codephylo.github.io/phylospec).
- Check out the [PRs](https://github.com/CODEPhylo/phylospec/pulls?q=is%3Apr+) and [our Blog](https://codephylo.github.io/phylospec/blog) for the latest progress.
- Check out the [VS Code extension](https://marketplace.visualstudio.com/items?itemName=CodePhylo.PhyloSpec) and the [OpenVSX extension](https://open-vsx.org/extension/CodePhylo/PhyloSpec).
- Check out the [other documentation](docs).

## Repository Structure

```
.
├── core/                             # Core implementations
│   └── java/                         # Java reference implementation
│       ├── src/main/java/            # Type system and annotations
│       │   └── org/phylospec/
│       │       ├── annotations/      # PhyloSpec annotations
│       │       ├── ast/              # Nodes of syntax tree
│       │       ├── components/       # Classes corresponding to components in component libraries
│       │       ├── converters/       # Classes to convert PhyloSpec into other languages (Rev, LPhy, JSON)
│       │       ├── domain/           # Bounded primitive types (PositiveReal, Probability, etc.)
│       │       ├── errors/           # Error types and reporting
│       │       ├── factory/          # Type factory utilities
│       │       ├── lexer/            # Lexer
│       │       ├── lsp/              # LSP Server
│       │       ├── parser/           # Parser
│       │       ├── templatematching/ # Template matching engine
│       │       ├── tiling/           # Tiling algorithm
│       │       ├── typeresolver/     # Type resolver and static type checker
│       │       └── types/            # Complex types (Matrix, Vector, etc.)
│       └── src/test/                 # Unit and integration tests
├── integrations/                     # Engine-specific integrations
│   └── beast3/java/                  # BEAST 3 integration
│   └── beastx/java/                  # BEAST X integration
├── scripts/                          # Misc. scripts for developers
├── tools/                            # Related tools
│   └── vscode/                       # VS Code Extension
│   └── phylospec-template-gui/       # Template-based GUI prototype
├── website/                          # Svelte website for the project
└── schema/                           # JSON schemas and specifications
    └── component-library.schema.json # Metaschema for component libraries
```

## Code Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) to automatically format the Java code. Formatting is enforced on every pull request (see `.github/workflows/pull_request.yml`).

To format the code locally, run:

```sh
sh scripts/format.sh
```

### Pre-commit hook

To automatically format your code before every commit, create a `pre-commit` file at `.git/hooks/pre-commit` with the following content:

```sh
#!/bin/sh
sh scripts/format.sh
```

Git will now check that your code is formatted on every commit.

### Fixing `git blame`

Large initial formatting commits can make `git blame` point to the formatting commit instead of the actual change. To make `git blame` skip these commits, configure git to use `.git-blame-ignore-revs`:

```sh
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

If a formatting commit isn't already listed in [.git-blame-ignore-revs](.git-blame-ignore-revs), add its commit hash to that file.

If you're using IntelliJ, you may need to restart it after configuring `blame.ignoreRevsFile` for the change to take effect.

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
  year = {2026},
  publisher = {GitHub},
  url = {https://github.com/CODEPhylo/phylospec}
}
```

## License

This project is licensed under the [MIT License](LICENSE).

## Contact

- **Issues**: [GitHub Issues](https://github.com/CODEPhylo/phylospec/issues)
- **Discussions**: [GitHub Discussions](https://github.com/CODEPhylo/phylospec/discussions)
