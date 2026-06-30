package tiling.runner;

import java.nio.file.Path;

public record FileRunPaths(
        Path sourcePath,
        String runName,
        Path outputDirectory,
        Path xmlPath
) {

    public FileRunPaths {
        if (sourcePath == null) {
            throw new IllegalArgumentException("sourcePath must not be null.");
        }

        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory must not be null.");
        }

        if (xmlPath == null) {
            throw new IllegalArgumentException("xmlPath must not be null.");
        }
    }

    public static FileRunPaths forSource(
            Path sourcePath,
            Path outputRoot
    ) {
        if (sourcePath == null) {
            throw new IllegalArgumentException("sourcePath must not be null.");
        }

        if (outputRoot == null) {
            throw new IllegalArgumentException("outputRoot must not be null.");
        }

        String runName =
                defaultRunName(sourcePath);

        Path outputDirectory =
                outputRoot.resolve(runName);

        return new FileRunPaths(
                sourcePath,
                runName,
                outputDirectory,
                outputDirectory.resolve(runName + ".xml")
        );
    }

    public static String defaultRunName(Path sourcePath) {
        String fileName =
                sourcePath.getFileName().toString();

        int extensionStart =
                fileName.lastIndexOf('.');

        if (extensionStart <= 0) {
            return fileName;
        }

        return fileName.substring(0, extensionStart);
    }
}
