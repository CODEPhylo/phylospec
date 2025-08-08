# PhyloSpec Core Java Implementation

This is the reference Java implementation of the PhyloSpec type system and annotations framework.

## Overview

The PhyloSpec Java library provides:

- **Type System**: A comprehensive set of types for phylogenetic modeling
- **Type Safety**: Runtime validation of all type constraints  
- **Annotations**: Framework for annotating phylogenetic models
- **Factory Methods**: Convenient type creation utilities
- **Interoperability**: Fully compliant with the PhyloSpec specification

## Type System

### Primitive Types

| Type | Description | Java Interface |
|------|-------------|----------------|
| `Real` | Real-valued number | `org.phylospec.types.Real` |
| `Integer` | Integer-valued number | `org.phylospec.types.Integer` |
| `Boolean` | Logical value | `org.phylospec.types.Boolean` |
| `String` | Text value | `org.phylospec.types.String` |

### Restricted Types

| Type | Base Type | Constraint | Java Interface |
|------|-----------|------------|----------------|
| `PositiveReal` | `Real` | > 0 | `org.phylospec.types.PositiveReal` |
| `NonNegativeReal` | `Real` | ≥ 0 | `org.phylospec.types.NonNegativeReal` |
| `Probability` | `Real` | [0, 1] | `org.phylospec.types.Probability` |
| `PositiveInteger` | `Integer` | > 0 | `org.phylospec.types.PositiveInteger` |

### Collection Types

| Type | Description | Java Interface |
|------|-------------|----------------|
| `Vector<T>` | Ordered collection | `org.phylospec.types.Vector<T>` |
| `Matrix<T>` | 2D array | `org.phylospec.types.Matrix<T>` |
| `SquareMatrix<T>` | Square matrix | `org.phylospec.types.SquareMatrix<T>` |

### Specialized Phylogenetic Types

| Type | Description | Constraints | Java Interface |
|------|-------------|-------------|----------------|
| `Simplex` | Probability vector | Elements sum to 1.0 | `org.phylospec.types.Simplex` |
| `StochasticMatrix` | Transition probability matrix | Rows sum to 1.0 | `org.phylospec.types.StochasticMatrix` |
| `QMatrix` | Rate matrix for CTMCs | Rows sum to 0, off-diagonals ≥ 0 | `org.phylospec.types.QMatrix` |

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>org.phylospec</groupId>
    <artifactId>phylospec-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Creating Types

```java
import org.phylospec.types.*;
import static org.phylospec.factory.PhyloSpecTypes.*;

// Basic types
Real r = real(3.14);
PositiveReal rate = positiveReal(0.5);
Probability p = probability(0.7);

// Collections
Vector<Real> v = vector(real(1.0), real(2.0), real(3.0));
Simplex freqs = simplex(0.25, 0.25, 0.25, 0.25);

// Matrices
QMatrix q = qMatrix(new double[][]{
    {-1.0,  0.5,  0.5},
    { 0.3, -0.6,  0.3},
    { 0.2,  0.4, -0.6}
});
```

### Using with Annotations

```java
@PhyloSpec(name = "JukesCantor", version = "1.0")
public class JukesCantorModel {
    
    @PhyloParam(name = "mu", description = "Substitution rate")
    private PositiveReal substitutionRate;
    
    @PhyloParam(name = "Q", description = "Rate matrix")
    private QMatrix rateMatrix;
    
    public JukesCantorModel(double mu) {
        this.substitutionRate = positiveReal(mu);
        this.rateMatrix = jukesCantor(mu);
    }
}
```

## Type Validation

All types validate their constraints at construction time:

```java
// Valid
Probability p1 = probability(0.5);  // OK

// Invalid - throws IllegalArgumentException
try {
    Probability p2 = probability(1.5);  // Error: > 1.0
} catch (IllegalArgumentException e) {
    // Handle invalid probability
}

// Simplex validation
Simplex s1 = simplex(0.6, 0.4);     // OK: sums to 1.0
Simplex s2 = simplex(0.5, 0.5, 0.5); // Error: sums to 1.5
```

## Prerequisites

- Java JDK 8 or later (JDK 11+ recommended)
- Apache Maven 3.6 or later
- Git

## Building from Source

```bash
# Clone the repository (if not already done)
git clone https://github.com/CODEPhylo/phylospec.git
cd phylospec/core/java

# Build and install to local Maven repository
mvn clean install

# Run tests only
mvn test

# Generate JavaDoc documentation
mvn javadoc:javadoc

# Build without running tests (faster)
mvn clean install -DskipTests
```

After successful build, you'll find:
- JAR file: `target/phylospec-core-1.0.0-SNAPSHOT.jar`
- JavaDoc: `target/site/apidocs/index.html`
- Sources JAR: `target/phylospec-core-1.0.0-SNAPSHOT-sources.jar`

## API Documentation

After building, API documentation is available at `target/site/apidocs/index.html`.

## Implementation Status

### Completed ✓
- Type interfaces for all core types
- Annotation framework (@PhyloSpec, @PhyloParam)
- Maven build configuration
- Comprehensive documentation

### In Progress 🚧
- Concrete implementations for type interfaces
- Factory class (PhyloSpecTypes)
- Unit tests for all types
- Example model implementations

### Planned 📋
- Serialization/deserialization support
- Integration with phylogenetic libraries (BEAST, etc.)
- Performance benchmarks
- Additional specialized types

## Project Structure

```
src/main/java/org/phylospec/
├── types/                    # Type interfaces
│   ├── PhyloSpecType.java   # Base interface
│   ├── Real.java            # Numeric types
│   ├── Integer.java
│   ├── Boolean.java
│   ├── String.java
│   ├── PositiveReal.java    # Constrained types
│   ├── NonNegativeReal.java
│   ├── Probability.java
│   ├── PositiveInteger.java
│   ├── Vector.java          # Collection types
│   ├── Matrix.java
│   ├── SquareMatrix.java
│   ├── Simplex.java         # Specialized types
│   ├── StochasticMatrix.java
│   └── QMatrix.java
├── types/impl/              # Implementations (to be added)
├── factory/                 # Factory utilities (to be added)
│   └── PhyloSpecTypes.java
├── annotations/             # Model annotations
│   ├── PhyloSpec.java
│   ├── PhyloParam.java
│   └── PhyloSpecRegistry.java
└── examples/                # Example models (to be added)
```

## Implementation Notes

The Java implementation strictly follows the PhyloSpec type specification with these key features:

- **Full descriptive names**: `PositiveInteger` instead of abbreviations
- **Runtime validation**: All type constraints are enforced at construction
- **Immutable types**: Type instances cannot be modified after creation
- **Type hierarchy**: Proper inheritance structure (e.g., `PositiveReal` extends `Real`)
- **Generic support**: Parameterized types like `Vector<T>` and `Matrix<T>`

## License

This project is licensed under the MIT License - see the [LICENSE](../../LICENSE) file for details.

## Citation

If you use PhyloSpec in your research, please cite:

```bibtex
@software{phylospec2025,
  author = {CODEPhylo steering group},
  title = {PhyloSpec: A Reference Specification for Phylogenetic Modeling Languages},
  year = {2025},
  url = {https://github.com/CODEPhylo/phylospec}
}
```