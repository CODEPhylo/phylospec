package org.phylospec.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.phylospec.components.EngineSpecificationSchema;

/**
 * Implements the LSP Workspace Service. It lets the user maintain the set of engines their models
 * are checked against, through the {@code phylospec.server.listEngines},
 * {@code phylospec.server.addEngine} and {@code phylospec.server.removeEngine} commands.
 *
 * <p>These names live in their own {@code phylospec.server} namespace on purpose: a client such as
 * the VS Code extension registers a command of its own for every command the server advertises, so
 * sharing a name with the client's user-facing commands would make the client fail to start.
 */
public class PhyloSpecWorkspaceService implements WorkspaceService {

    static final String LIST_ENGINES_COMMAND = "phylospec.server.listEngines";
    static final String ADD_ENGINE_COMMAND = "phylospec.server.addEngine";
    static final String REMOVE_ENGINE_COMMAND = "phylospec.server.removeEngine";

    static final List<String> COMMANDS = List.of(LIST_ENGINES_COMMAND, ADD_ENGINE_COMMAND, REMOVE_ENGINE_COMMAND);

    private final EngineRegistry engineRegistry;
    private final PhyloSpecTextDocumentService textService;

    PhyloSpecWorkspaceService(EngineRegistry engineRegistry, PhyloSpecTextDocumentService textService) {
        this.engineRegistry = engineRegistry;
        this.textService = textService;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {}

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        List<String> arguments = getArguments(params);

        return CompletableFuture.supplyAsync(() -> switch (params.getCommand()) {
            case LIST_ENGINES_COMMAND -> listEngines();
            case ADD_ENGINE_COMMAND -> addEngine(arguments);
            case REMOVE_ENGINE_COMMAND -> removeEngine(arguments);
            default -> failure("There is no command '" + params.getCommand() + "'.");
        });
    }

    /* the commands */

    /**
     * Lists every engine the repository offers, together with the engines the user picked.
     */
    private CommandResult listEngines() {
        List<String> availableEngines = engineRegistry.getAvailableEngines().stream()
                .map(EngineSpecificationSchema::getName)
                .toList();

        if (availableEngines.isEmpty()) {
            return failure("The PhyloSpec repository offers no engines. Are you offline?");
        }

        return new CommandResult(true, "", availableEngines, selectedEngines());
    }

    /**
     * Picks the engine the first argument names, in the version the second argument names if there
     * is one.
     */
    private CommandResult addEngine(List<String> arguments) {
        if (arguments.isEmpty()) return failure("Name the engine you want to add.");

        String name = arguments.getFirst();
        String version = 1 < arguments.size() ? arguments.get(1) : null;

        if (!engineRegistry.addEngine(name, version)) {
            return failure(
                    version == null
                            ? "The repository holds no engine '" + name + "'."
                            : "The repository holds no engine '" + name + "' in the version " + version + ".");
        }

        // the diagnostics of every open document depend on the engines
        textService.reanalyzeAll();

        return success("Added '" + name + "'.");
    }

    /**
     * Drops the engine the first argument names.
     */
    private CommandResult removeEngine(List<String> arguments) {
        if (arguments.isEmpty()) return failure("Name the engine you want to remove.");

        String name = arguments.getFirst();

        if (!engineRegistry.removeEngine(name)) {
            return failure("'" + name + "' is not one of your engines.");
        }

        // the diagnostics of every open document depend on the engines
        textService.reanalyzeAll();

        return success("Removed '" + name + "'.");
    }

    private List<String> selectedEngines() {
        return engineRegistry.getSelectedEngineNames();
    }

    private CommandResult success(String description) {
        return new CommandResult(true, description, List.of(), selectedEngines());
    }

    private CommandResult failure(String description) {
        return new CommandResult(false, description, List.of(), selectedEngines());
    }

    /* helper functions */

    /**
     * Returns the command arguments as strings. The client sends them as JSON, so a string argument
     * arrives as a JSON value that still carries its quotes. We do not read the JSON library the
     * language server protocol uses, so we unquote the values ourselves.
     */
    private static List<String> getArguments(ExecuteCommandParams params) {
        if (params.getArguments() == null) return List.of();

        List<String> arguments = new ArrayList<>();

        for (Object argument : params.getArguments()) {
            if (argument == null) continue;
            arguments.add(unquote(argument.toString()));
        }

        return arguments;
    }

    /**
     * Strips the quotes a JSON string carries, if it carries any.
     */
    private static String unquote(String value) {
        if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) return value;

        return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * What a command did, sent back to the client so that it can tell the user. Only
     * {@code phylospec.server.listEngines} fills in the available engines.
     */
    public record CommandResult(
            boolean succeeded, String description, List<String> availableEngines, List<String> selectedEngines) {}
}
