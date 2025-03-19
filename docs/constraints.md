# PhyloSpec Constraint System

This document defines the constraint system in PhyloSpec. Constraints provide a way to restrict values and relationships between parameters in a phylogenetic model.

## 1. Constraint Types

### 1.1 Numeric Constraints

#### `LessThan(left: Real, right: Real) -> Constraint`

Ensures that the left value is less than the right value.

| Parameter | Type   | Description            | Default | Constraints |
|-----------|--------|------------------------|---------|-------------|
| `left`    | `Real` | Value that should be less | None  | None        |
| `right`   | `Real` | Value that should be greater | None | None     |

#### `GreaterThan(left: Real, right: Real) -> Constraint`

Ensures that the left value is greater than the right value.

| Parameter | Type   | Description              | Default | Constraints |
|-----------|--------|--------------------------|---------|-------------|
| `left`    | `Real` | Value that should be greater | None | None      |
| `right`   | `Real` | Value that should be less | None   | None       |

#### `Equals(left: T, right: T) -> Constraint`

Enforces equality between two values of the same type.

| Parameter | Type | Description                 | Default | Constraints |
|-----------|----- |----------------------------|---------|-------------|
| `left`    | `T`  | First value to compare     | None    | None        |
| `right`   | `T`  | Second value to compare    | None    | None        |

#### `Bounded(variable: Real, lower: Real, upper: Real) -> Constraint`

Restricts a parameter to a specific range.

| Parameter  | Type   | Description            | Default | Constraints     |
|------------|--------|------------------------|---------|----------------|
| `variable` | `Real` | Value to constrain     | None    | None           |
| `lower`    | `Real` | Lower bound (inclusive)| None    | <= upper       |
| `upper`    | `Real` | Upper bound (inclusive)| None    | >= lower       |

#### `SumTo(variables: Vector<Real>, target: Real) -> Constraint`

Requires a set of parameters to sum to a target value.

| Parameter   | Type            | Description            | Default | Constraints |
|-------------|-----------------|------------------------|---------|-------------|
| `variables` | `Vector<Real>`  | Values to sum          | None    | Not empty   |
| `target`    | `Real`          | Target sum value       | None    | None        |

### 1.2 Tree Constraints

#### `Monophyly(taxa: TaxonSet, tree: Tree) -> Constraint`

Requires a set of taxa to form a monophyletic group in the tree.

| Parameter | Type       | Description             | Default | Constraints        |
|-----------|------------|-------------------------|---------|-------------------|
| `taxa`    | `TaxonSet` | Taxa to constrain       | None    | At least two taxa |
| `tree`    | `Tree`     | Tree to apply constraint| None    | None              |

#### `Calibration(node: TreeNode, distribution: Distribution<Real>) -> Constraint`

Age calibration for a node in the tree.

| Parameter      | Type                    | Description             | Default | Constraints            |
|----------------|-------------------------|-------------------------|---------|------------------------|
| `node`         | `TreeNode`              | Node to calibrate       | None    | Not a leaf node        |
| `distribution` | `Distribution<Real>`    | Age prior distribution  | None    | Appropriate for ages   |

#### `FixedTopology(tree: Tree, topology: Tree) -> Constraint`

Fixes the topology of a tree to match a reference topology.

| Parameter  | Type   | Description                | Default | Constraints                 |
|------------|--------|----------------------------|---------|----------------------------|
| `tree`     | `Tree` | Tree to constrain          | None    | None                       |
| `topology` | `Tree` | Reference topology         | None    | Same taxa as target tree   |

### 1.3 Sequence Constraints

#### `MolecularClock(tree: Tree) -> Constraint`

Enforces a strict molecular clock on a tree.

| Parameter | Type   | Description      | Default | Constraints |
|-----------|--------|------------------|---------|-------------|
| `tree`    | `Tree` | Tree to constrain| None    | None        |

### 1.4 Complex Constraints

#### `CompoundConstraint(constraints: Vector<Constraint>, operator: String) -> Constraint`

Combines multiple constraints with a logical operator.

| Parameter     | Type                 | Description              | Default | Constraints                 |
|---------------|----------------------|--------------------------|---------|----------------------------|
| `constraints` | `Vector<Constraint>` | Constraints to combine   | None    | At least one constraint    |
| `operator`    | `String`             | Logical operator         | "AND"   | One of: "AND", "OR", "XOR" |

#### `Correlation(variables: Vector<Real>, threshold: Probability) -> Constraint`

Enforces a minimum correlation between variables.

| Parameter    | Type            | Description              | Default | Constraints       |
|--------------|-----------------|--------------------------|---------|------------------|
| `variables`  | `Vector<Real>`  | Variables to correlate   | None    | At least two     |
| `threshold`  | `Probability`   | Minimum correlation      | 0.0     | 0 <= value <= 1  |

## 2. Constraint Application

### 2.1 Model-Level Constraints

Constraints can be applied at the model level to enforce relationships between parameters:

```
constraints: [
  LessThan(left: "birthRate", right: "deathRate"),
  SumTo(variables: ["freq1", "freq2", "freq3", "freq4"], target: 1.0)
]
```

### 2.2 Distribution-Level Constraints

Some distributions have built-in constraints on their parameters:

```
Uniform(lower: 0.0, upper: 10.0)  // Implicitly applies Bounded constraint
```

### 2.3 Dynamic Constraints

Constraints can dynamically reference other model parameters:

```
Bounded(variable: "kappa", lower: 0.0, upper: "maxKappa")
```

## 3. Implementation Requirements

Language implementations must:

1. Support all constraint types with their parameters
2. Enforce constraints during model validation
3. Allow constraints to reference model parameters
4. Provide error messages when constraints are violated
5. Handle compound constraints correctly

### 3.1 Constraint Checking

Constraints should be checked at these times:

1. During model validation (before execution)
2. When parameter values change
3. During initialization

### 3.2 Constraint Satisfaction

When constraints cannot be satisfied, implementations should:

1. Provide clear error messages indicating which constraint failed
2. Include the current values of relevant parameters
3. Suggest possible fixes if applicable

## 4. Extension Mechanisms

Implementations may extend the constraint system by:

1. Adding additional constraint types
2. Supporting more complex logical combinations
3. Providing optimization algorithms for constraint satisfaction
4. Implementing constraint propagation techniques

## Reference

For machine-readable definitions, see [constraints.json](../../schema/constraints.json) in the schema directory.