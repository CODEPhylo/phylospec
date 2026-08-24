import { ChildProcessWithoutNullStreams, execFile, spawn } from "child_process";
import { existsSync } from "fs";
import { connect, Socket } from "net";
import { promisify } from "util";
import * as vscode from "vscode";

import {
  ExecuteCommandRequest,
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

// the server advertises these commands, and the language client registers a VS Code command for
// each of them, so they must not collide with the user-facing commands the extension registers

const LIST_ENGINES_COMMAND = "phylospec.server.listEngines";
const ADD_ENGINE_COMMAND = "phylospec.server.addEngine";
const REMOVE_ENGINE_COMMAND = "phylospec.server.removeEngine";

let client: LanguageClient;
let serverProcess: ChildProcessWithoutNullStreams | undefined;
let outputChannel: vscode.OutputChannel;

export async function activate(context: vscode.ExtensionContext) {
  outputChannel = vscode.window.createOutputChannel("PhyloSpec LSP");
  context.subscriptions.push(outputChannel);

  const jarPath = context.asAbsolutePath(SERVER_JAR);

  if (!existsSync(jarPath)) {
    vscode.window.showErrorMessage(`PhyloSpec LSP JAR not found: ${jarPath}`);
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
    return;
  }

  registerEngineCommands(context);
}

// the engines a model is checked against are kept by the language server, so the commands are
// thin wrappers that ask the user what to do and hand it over

interface CommandResult {
  succeeded: boolean;
  description: string;
  availableEngines: string[];
  selectedEngines: string[];
}

async function runServerCommand(
  command: string,
  args: string[] = [],
): Promise<CommandResult | undefined> {
  try {
    return await client.sendRequest(ExecuteCommandRequest.type, {
      command,
      arguments: args,
    });
  } catch (error) {
    vscode.window.showErrorMessage(`PhyloSpec: ${error}`);
    return undefined;
  }
}

function reportResult(result: CommandResult) {
  if (!result.succeeded) {
    vscode.window.showWarningMessage(`PhyloSpec: ${result.description}`);
    return;
  }

  const engines = result.selectedEngines.length
    ? result.selectedEngines.join(", ")
    : "none";
  vscode.window.showInformationMessage(
    `PhyloSpec: ${result.description} Your engines: ${engines}.`,
  );
}

function registerEngineCommands(context: vscode.ExtensionContext) {
  context.subscriptions.push(
    vscode.commands.registerCommand("phylospec.addEngine", async () => {
      const available = await runServerCommand(LIST_ENGINES_COMMAND);
      if (!available) return;

      if (!available.succeeded) {
        vscode.window.showWarningMessage(
          `PhyloSpec: ${available.description}`,
        );
        return;
      }

      const choices = available.availableEngines.filter(
        (engine) => !available.selectedEngines.includes(engine),
      );

      if (choices.length === 0) {
        vscode.window.showInformationMessage(
          "PhyloSpec: You already use every engine the repository offers.",
        );
        return;
      }

      const engine = await vscode.window.showQuickPick(choices, {
        title: "Add a PhyloSpec engine",
        placeHolder: "Pick the engine your models should run on",
      });
      if (!engine) return;

      const result = await runServerCommand(ADD_ENGINE_COMMAND, [engine]);
      if (result) reportResult(result);
    }),
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("phylospec.removeEngine", async () => {
      const selected = await runServerCommand(LIST_ENGINES_COMMAND);
      if (!selected) return;

      if (selected.selectedEngines.length === 0) {
        vscode.window.showInformationMessage(
          "PhyloSpec: You do not use any engine at the moment.",
        );
        return;
      }

      const engine = await vscode.window.showQuickPick(
        selected.selectedEngines,
        {
          title: "Remove a PhyloSpec engine",
          placeHolder: "Pick the engine you no longer want to check against",
        },
      );
      if (!engine) return;

      const result = await runServerCommand(REMOVE_ENGINE_COMMAND, [engine]);
      if (result) reportResult(result);
    }),
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("phylospec.listEngines", async () => {
      const result = await runServerCommand(LIST_ENGINES_COMMAND);
      if (!result) return;

      if (!result.succeeded) {
        vscode.window.showWarningMessage(`PhyloSpec: ${result.description}`);
        return;
      }

      const selected = result.selectedEngines.length
        ? result.selectedEngines.join(", ")
        : "none";
      vscode.window.showInformationMessage(
        `PhyloSpec: Your engines: ${selected}. Available: ${result.availableEngines.join(", ")}.`,
      );
    }),
  );
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
