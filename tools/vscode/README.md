# PhyloSpec VS Code Extension

This repository contains a VS Code extension with two main features:

- Syntay highlighting controlled by the TextMate grammar in `syntaxes/phylospec.json`.
- A barebone LSP client connecting to the (running) Java LSP on port 5007.

## Getting Started

To run a developer version of this extension in VS Code (or any of its forks):

1. Clone this repo
2. Run `npm install` from this directory..
3. Run `npm run compile` from this directory.
4. Start the Java LSP (`org.phylospec.lsp.RunLsp`).
5. Open the `src/extension.ts` file and press `F5`.

A new window should pop up. Create a new `.phylospec` file and try out the extension.
