import { connect } from "net";

import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo,
} from "vscode-languageclient/node";

const PORT = 5007;

let client: LanguageClient;

export function activate() {
  let connectionInfo = {
    port: PORT,
    host: "localhost"
  };

  let serverOptions = () => {
    let socket = connect(connectionInfo);
    let result: StreamInfo = {
      writer: socket,
      reader: socket
    };
    return Promise.resolve(result);
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "*" }],
  };

  client = new LanguageClient(
    "phylospeclsp",
    "PhyloSpec",
    serverOptions,
    clientOptions
  );
  client.start();
}

export function deactivate(): Thenable<void> | undefined {
  if (!client) {
    return undefined;
  }
  return client.stop();
}
