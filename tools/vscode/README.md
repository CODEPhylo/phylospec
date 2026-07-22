# PhyloSpec VS Code Extension

This repository contains a VS Code extension with two main features:

- Syntay highlighting controlled by the TextMate grammar in `syntaxes/phylospec.json`.
- A barebone LSP client launching and using the Java LSP server.

## Getting Started

To run a developer version of this extension in VS Code (or any of its forks):

1. Clone this repo.
2. Run `npm install` from this directory.
3. Run `npm run vscode:prepublish` from this directory.
4. **Open VS Code in this directory** (not in the root directory of this repository). Open the `src/extension.ts` file, press `F5`, and select `Extension Development Host`.

A new window should pop up. Create a new `.phylospec` file and try out the extension.
