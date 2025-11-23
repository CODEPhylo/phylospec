# Specification

We introduce the __component library format__, a machine-readable way to define what components should exist and what their interfaces should look like.

!> The JSON Component Library Format does *not* define the components themselves, but provides a standardized way to describe them.

## JSON Component Library Format

PhyloSpec uses a <a href="https://github.com/CODEPhylo/phylospec/blob/main/schema/component-library.schema.json" target="_blank">standardized JSON schema</a> to describe available components. It formalizes how to describe:

- **Types** — Primitive types, collections, and phylogenetic-specific types with inheritance and constraints
- **Generators** — Distributions and functions with typed parameters

The JSON format supports parameterized types and generators, type inheritance, parameter constraints, dimension expressions, default values, optional parameters, and more.

The [PhyloSpec Core Component Library](components) follows the Component Library Format and defines the built-in PhyloSpec model components. Inference engine or method developers __can describe their own models__ by providing a JSON file adhering to the same format.

Example type described using the format:

```json
{
    "name": "Sequence",
    "description": "Biological sequence with elements from alphabet A",
    "namespace": "phylospec.types",
    "typeParameters": ["A"],
    "properties": {
        "length": {
        "type": "Integer",
        "description": "Length of the sequence"
        },
        "alphabet": {
        "type": "String",
        "description": "Alphabet type (e.g., 'Nucleotide', 'AminoAcid')"
        }
    }
}
```

Example generator described using the format:

```json
{
    "name": "IID",
    "generatorType": "distribution",
    "generatedType": "Distribution<Vector<T>>",
    "namespace": "phylospec.distributions",
    "description": "Vector of independent and identically distributed random variables",
    "typeParameters": ["T"],
    "arguments": [
        {
        "name": "base",
        "type": "Distribution<T>",
        "description": "Base distribution for each component",
        "required": true
        },
        {
        "name": "n",
        "type": "PositiveInteger",
        "description": "Number of independent draws",
        "required": true
        }
    ]
}
```

## Engine Integration Format

The **engine integration format** allows engines to document engine-specific limitations in a standardized and machine-readable way. The limitations include unsupported core components, arguments which cannot be random variables, and more.
