# Building PhyloSpec Annotations

This document explains how to build and use the PhyloSpec annotation library.

## Prerequisites

- Java JDK 11 or later
- Apache Maven 3.6 or later
- Git

## Building from Source

1. Clone the repository if you haven't already:
   ```bash
   git clone https://github.com/CODEPhylo/phylospec.git
   cd phylospec
   ```

2. Navigate to the Java adapter directory:
   ```bash
   cd adapters/java
   ```

3. Build with Maven:
   ```bash
   mvn clean install
   ```

   This will:
   - Compile the Java code
   - Run the tests
   - Install the artifacts to your local Maven repository
   - Generate the JAR files in the `target` directory

4. After successful build, you'll find these files in the `target` directory:
   - `phylospec-annotations-0.1.0-SNAPSHOT.jar` - The main JAR file
   - `phylospec-annotations-0.1.0-SNAPSHOT-sources.jar` - Source code JAR
   - `phylospec-annotations-0.1.0-SNAPSHOT-javadoc.jar` - Documentation JAR

## Using in Other Projects

### Maven Projects

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.phylospec</groupId>
    <artifactId>phylospec-annotations</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Gradle Projects

Add the following to your `build.gradle`:

```groovy
dependencies {
    implementation 'org.phylospec:phylospec-annotations:0.1.0-SNAPSHOT'
}
```

### Manual JAR Installation

If you're not using Maven or Gradle, you can add the JAR file directly to your project's classpath.

## Publishing to Maven Central

To publish the library to Maven Central (for easier dependency management):

1. Make sure you have GPG set up for signing artifacts
2. Update the version in `pom.xml` to a release version (remove -SNAPSHOT)
3. Run Maven with the release profile:
   ```bash
   mvn clean deploy -P release
   ```
   
Note: The commented-out sections in `pom.xml` related to Maven Central deployment would need to be uncommented and properly configured before publishing.
