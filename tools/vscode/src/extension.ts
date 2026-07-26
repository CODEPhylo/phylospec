import { ChildProcessWithoutNullStreams, execFile, spawn } from "child_process";
import { existsSync } from "fs";
import { connect, Socket } from "net";
import { promisify } from "util";
import * as vscode from "vscode";

import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo,
} from "vscode-languageclient/node";

const PORT = 5007;
const HOST = "localhost";
const SERVER_JAR = "server/phylospec-lsp.jar";
const STARTUP_ATTEMPTS = 20;
const STARTUP_RETRY_MS = 250;
const REQUIRED_JAVA_VERSION = 25;
const execFileAsync = promisify(execFile);

let client: LanguageClient;
let serverProcess: ChildProcessWithoutNullStreams | undefined;
let outputChannel: vscode.OutputChannel;

export async function activate(context: vscode.ExtensionContext) {
  outputChannel = vscode.window.createOutputChannel("PhyloSpec LSP");
  context.subscriptions.push(outputChannel);

  const jarPath = context.asAbsolutePath(SERVER_JAR);

  if (!existsSync(jarPath)) {
    vscode.window.showErrorMessage(`PhyloSpec LSP JAR not found: ${jarPath}`);
    context.asAbsolutePath();
    return;
  }

  const javaVersion = await getJavaVersion();

  if (javaVersion === undefined) {
    vscode.window.showErrorMessage(
      "PhyloSpec requires Java 25 or newer, but Java could not be found. Install Java 25 and ensure the java executable is available on your PATH.",
    );
    return;
  }

  if (javaVersion < REQUIRED_JAVA_VERSION) {
    vscode.window.showErrorMessage(
      `PhyloSpec requires Java 25 or newer, but Java ${javaVersion} was found. Install Java 25 and ensure it is the java executable available on your PATH.`,
    );
    return;
  }

  try {
    startServer(jarPath);
  } catch (error) {
    vscode.window.showErrorMessage(`Failed to start PhyloSpec LSP: ${error}`);
    return;
  }

  const connectionInfo = {
    port: PORT,
    host: HOST,
  };

  const serverOptions = async () => {
    const socket = await connectToServer(connectionInfo);
    const result: StreamInfo = {
      writer: socket,
      reader: socket,
    };
    return result;
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "phylospec" }],
  };

  client = new LanguageClient(
    "phylospeclsp",
    "PhyloSpec",
    serverOptions,
    clientOptions,
  );

  try {
    await client.start();
  } catch (error) {
    vscode.window.showErrorMessage(
      `Failed to connect to PhyloSpec LSP: ${error}`,
    );
    if (serverProcess) {
      serverProcess.kill();
      serverProcess = undefined;
    }
  }
}

async function getJavaVersion(): Promise<number | undefined> {
  try {
    const { stdout, stderr } = await execFileAsync("java", ["-version"]);
    const versionOutput = `${stdout}\n${stderr}`;
    const match = versionOutput.match(/version "(?:1\.)?(\d+)/);
    return match ? Number.parseInt(match[1], 10) : undefined;
  } catch {
    return undefined;
  }
}

function startServer(jarPath: string) {
  serverProcess = spawn("java", ["-jar", jarPath]);

  serverProcess.stdout.on("data", (data: Buffer) => {
    outputChannel.append(data.toString());
  });

  serverProcess.stderr.on("data", (data: Buffer) => {
    outputChannel.append(data.toString());
  });

  serverProcess.on("error", (error: Error) => {
    vscode.window.showErrorMessage(
      `Failed to start PhyloSpec LSP. Is Java installed? ${error.message}`,
    );
  });

  serverProcess.on(
    "exit",
    (code: number | null, signal: NodeJS.Signals | null) => {
      outputChannel.appendLine(
        `PhyloSpec LSP exited with code ${code} and signal ${signal}`,
      );
    },
  );
}

function connectToServer(connectionInfo: {
  port: number;
  host: string;
}): Promise<Socket> {
  return new Promise((resolve, reject) => {
    let attempt = 0;

    const tryConnect = () => {
      attempt++;

      const socket = connect(connectionInfo, () => {
        resolve(socket);
      });

      socket.on("error", (error: NodeJS.ErrnoException) => {
        socket.destroy();

        if (attempt < STARTUP_ATTEMPTS) {
          setTimeout(tryConnect, STARTUP_RETRY_MS);
          return;
        }

        reject(error);
      });
    };

    tryConnect();
  });
}

export async function deactivate(): Promise<void> {
  if (client) {
    await client.stop();
  }

  if (serverProcess) {
    serverProcess.kill();
    serverProcess = undefined;
  }
}
