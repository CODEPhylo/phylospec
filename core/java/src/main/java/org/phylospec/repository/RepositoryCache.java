package org.phylospec.repository;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;

/**
 * Keeps local clones of component repositories in a cache on disk.
 * <p>
 * The cache allows to work offline and makes sure that data is only transferred when the
 * remote repository has actually changed.
 */
class RepositoryCache {

    private static final String CACHE_DIRECTORY_NAME = ".phylospec";
    private static final String REPOSITORIES_DIRECTORY_NAME = "repositories";

    /**
     * Returns the path to the local clone of the given repository, cloning or updating it if needed.
     * <p>
     * If the remote cannot be reached, an already existing local clone is used instead. If there is
     * no local clone either, an {@link IOException} is thrown.
     */
    static Path getRepositoryPath(String repoUri) throws IOException {
        Path repositoryPath = getCachePath(repoUri);
        Files.createDirectories(repositoryPath.getParent());

        // we lock the repository so that concurrent processes (e.g. the CLI and the language
        // server) do not clone or update the same directory at the same time

        Path lockPath = repositoryPath.resolveSibling(repositoryPath.getFileName() + ".lock");

        try (RandomAccessFile lockFile = new RandomAccessFile(lockPath.toFile(), "rw");
                FileLock ignored = lockFile.getChannel().lock()) {
            updateRepository(repoUri, repositoryPath);
        }

        return repositoryPath;
    }

    /**
     * Clones the repository if it does not exist yet, and updates it if the remote has changed.
     */
    private static void updateRepository(String repoUri, Path repositoryPath) throws IOException {
        boolean isCloned = Files.isDirectory(repositoryPath.resolve(".git"));

        String remoteCommit;
        try {
            remoteCommit = getRemoteCommit(repoUri);
        } catch (GitAPIException e) {
            // we are most likely offline, so we fall back to the cached clone if we have one
            if (isCloned) return;
            throw new IOException(
                    "The component repository '" + repoUri + "' could not be reached and is not cached.", e);
        }

        try {
            if (!isCloned) {
                Git.cloneRepository()
                        .setURI(repoUri)
                        .setDirectory(repositoryPath.toFile())
                        .setDepth(1)
                        .call()
                        .close();
                return;
            }

            try (Git git = Git.open(repositoryPath.toFile())) {
                ObjectId localCommit = git.getRepository().resolve("HEAD");

                // the cached clone is already up-to-date, so we do not transfer anything
                if (localCommit != null && localCommit.name().equals(remoteCommit)) return;

                git.fetch().setDepth(1).call();
                git.reset()
                        .setMode(ResetCommand.ResetType.HARD)
                        .setRef(remoteCommit)
                        .call();
            }
        } catch (GitAPIException e) {
            // the transfer failed, so we fall back to the cached clone if we have one
            if (isCloned) return;
            throw new IOException("The component repository '" + repoUri + "' could not be downloaded.", e);
        }
    }

    /**
     * Returns the commit the remote repository currently points at, without cloning it.
     */
    private static String getRemoteCommit(String repoUri) throws GitAPIException {
        Map<String, Ref> remoteRefs =
                Git.lsRemoteRepository().setRemote(repoUri).callAsMap();

        for (String refName : new String[] {"HEAD", "refs/heads/main", "refs/heads/master"}) {
            Ref ref = remoteRefs.get(refName);
            if (ref != null && ref.getObjectId() != null)
                return ref.getObjectId().name();
        }

        throw new IllegalArgumentException(
                "The component repository '" + repoUri + "' does not have a main or master branch.");
    }

    /* helper functions for the cache layout */

    /**
     * Returns the directory the given repository is cached in.
     * This is `~/.phylospec/repositories/<host>/<owner>/<repo>;`.
     */
    private static Path getCachePath(String repoUri) {
        Path cachePath = Paths.get(System.getProperty("user.home"), CACHE_DIRECTORY_NAME, REPOSITORIES_DIRECTORY_NAME);

        for (String segment : getCacheSegments(repoUri)) {
            cachePath = cachePath.resolve(segment);
        }

        return cachePath;
    }

    /**
     * Splits the repository URI into the path segments it is cached under.
     */
    private static String[] getCacheSegments(String repoUri) {
        String host;
        String path;

        try {
            URIish parsedUri = new URIish(repoUri);
            host = parsedUri.getHost();
            path = parsedUri.getPath();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("'" + repoUri + "' is not a valid repository URI.", e);
        }

        if (host == null || host.isEmpty()) host = "local";
        if (path == null) path = "";
        if (path.endsWith(".git")) path = path.substring(0, path.length() - ".git".length());

        return Arrays.stream((host + "/" + path).split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(RepositoryCache::sanitize)
                .toArray(String[]::new);
    }

    /**
     * Replaces all characters which are not safe to use in a path.
     */
    private static String sanitize(String segment) {
        return segment.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
