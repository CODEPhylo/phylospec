"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const net_1 = require("net");
const vscode_1 = require("vscode");
const node_1 = require("vscode-languageclient/node");
let client;
function activate(context) {
    let connectionInfo = {
        port: 5007,
        host: "localhost"
    };
    let serverOptions = () => {
        // Connect to language server via socket
        let socket = (0, net_1.connect)(connectionInfo);
        let result = {
            writer: socket,
            reader: socket
        };
        return Promise.resolve(result);
    };
    const clientOptions = {
        // Register the server for all documents by default
        documentSelector: [{ scheme: "file", language: "*" }],
        synchronize: {
            // Notify the server about file changes to '.clientrc files contained in the workspace
            fileEvents: vscode_1.workspace.createFileSystemWatcher("**/.clientrc"),
        },
    };
    // Create the language client and start the client.
    client = new node_1.LanguageClient("phylospeclsp", "PhyloSpec", serverOptions, clientOptions);
    // Start the client. This will also launch the server
    client.start();
}
function deactivate() {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
//# sourceMappingURL=extension.js.map