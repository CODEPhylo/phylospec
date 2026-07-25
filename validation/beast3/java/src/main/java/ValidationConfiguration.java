import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared configuration and path handling for BEAST 3 validation entry points.
 */
final class ValidationConfiguration {

    private static final String PREFIX =
            "phylospec.validation.";
    private static final String REPOSITORY_ROOT_PLACEHOLDER =
            "${REPOSITORY_ROOT}";

    private ValidationConfiguration() {
    }

    static String required(String name) {
        String propertyName =
                PREFIX + name;
        String value =
                System.getProperty(propertyName);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing system property: " + propertyName
            );
        }

        return value;
    }

    static Path requiredPath(String name) {
        return resolve(required(name));
    }

    static Path optionalPath(String name) {
        String value =
                System.getProperty(PREFIX + name);

        return value == null || value.isBlank()
                ? null
                : resolve(value);
    }

    static Path repositoryRoot() {
        return Path.of(required("repositoryRoot"))
                .toAbsolutePath()
                .normalize();
    }

    static String readSource(String name) throws IOException {
        Path sourcePath =
                requiredPath(name);

        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException(
                    "PhyloSpec source does not exist: " + sourcePath
            );
        }

        return Files.readString(sourcePath)
                .replace(
                        REPOSITORY_ROOT_PLACEHOLDER,
                        repositoryRoot().toString()
                );
    }

    private static Path resolve(String value) {
        Path path =
                Path.of(value);

        return path.isAbsolute()
                ? path.normalize()
                : repositoryRoot().resolve(path).normalize();
    }
}
