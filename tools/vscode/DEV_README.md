# Developer Infos

## Getting Started

To run a developer version of this extension in VS Code (or any of its forks):

1. Clone this repo.
2. Make sure `java`, `mvn`, and `npm` are available on your `PATH`.
3. Run `npm install` from this directory.
4. Run `npm run vscode:prepublish` from this directory. This builds the Java LSP fat JAR, copies it to `server/phylospec-lsp.jar`, and compiles the TypeScript extension.
5. **Open VS Code in this directory** (not in the root directory of this repository). Open the `src/extension.ts` file, press `F5`, and select `Extension Development Host`.

A new window should pop up. Create or open a `.phylospec` file and try out the extension. The extension activates for PhyloSpec files and starts the bundled Java LSP server on port `5007`.

## How to Publish

1. Login with your Azure account which is connected to the PhyloSpec organization using `vsce login CodePhylo`.
2. Make sure you've increased the version number in the `package.json` file.
3. Run `npm run publish` to publish the VS Code extension.