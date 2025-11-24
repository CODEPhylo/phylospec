# Modeling Language

This page describes the philosophy and features of the modeling language.

## Language Philosophy

- PhyloSpec is a language for describing phylogenetic models. It is _not_ a programming language.
- PhyloSpec aims to be expressive enough to describe a wide range of models, even if they cannot be efficiently inferred in current engines. However, its scope is explicitly restricted to __phylogenetic models__. Things like proposal distributions or the specific inference algorithm are not part of the core language.
- PhyloSpec is designed to be __human-readable__. It should be concise and easy to understand.
- PhyloSpec is designed to be __declarative__. Models should describe _what_ the relationships are, not _how_ to compute them. This promotes clarity, prevents side effects, and aligns with mathematical notation.
- PhyloSpec is designed to __prevent invalid models__. Types and other language features are used to detect invalid models before inference.
- PhyloSpec is designed to be __extensible__. Engines can add custom types, distributions, and functions to the language.

## Language Features

This example shows some features of the language:

```phylospec
Alignment observedSequences = nexus("alignment.nex")

QMatrix substModel = HKY(
    kappa~LogNormal(meanlog=1.0, sdlog=0.5),
    baseFrequencies~Dirichlet(alpha=[1.0, 1.0, 1.0, 1.0])
)

Tree phylogeny ~ Yule(
    birthRate~Exponential(10.0),
    taxa=observedSequences.taxa
)

@observedAs(observedSequences)
Alignment sequences ~ PhyloCTMC(
    tree=phylogeny, 
    Q=substModel, 
    numSequences=observedSequences.ntax
)
```

### General Syntax

PhyloSpec models describe a graphical model. Every statement corresponds to one variable in the graph.

The simplest statement is a constant assignment:

```phylospec
Rate birthRate = 2.5
```

This defines a new variable called `birthRate` of type `Rate` with a constant value of `2.5`. Every variable has a type. Whenever possible, PhyloSpec uses types that tell you what a variable actually represents. This is why `birthRate` has type `Rate` and not just `PositiveReal`.

There are other basic types:

```phylospec
String taxonName = "Chimpanzee"
Integer number = 100
Vector<String> taxonNames = ["Chimpanzee", "Human"]
```

We can apply functions and the usual numerical expressions:

```phylospec
Rate deathRate = 0.5 * birthRate
Rate diversificationRate = birthRate - deathRate
Real logDiversificationRate = log(diversificationRate)
```

Functions can create more sophisticated types:

```phylospec
Alignment alignment = nexus(file="sequences.nex")
Count numTaxa = alignment.numTaxa
TaxonSet taxa = alignment.taxa
```

So far, we only have deterministic variables. Let's change that!

```phylospec
Distribution<Real> normalDistribution = Normal(mean=0.0, sd=1.0)
Real drawnValue ~ normalDistribution
```

Here, `Normal` is a function which returns a `Distribution<Real>` object (a distribution on real numbers). This distribution is then assigned to the *random variable* `drawnValue` using the `~` operator. This is where the randomness in our model comes from!

`~` assigns a distribution to a random variable, hence it always has to be preceded by a `Distribution` object. Use the `=` operator for assignments of constant values or for deterministic transformations of random variables.

Some examples of valid and invalid statements:

```phylospec
// ✅ Exponential is a function which returns a Distribution<PositiveReal>
PositiveReal a ~ Exponential(rate=1.0)

// ✅
Real b = log(a)

// 🚫 b is not a distribution
Real c ~ b                          

// 🚫 can't add a number to a distribution object
Real d ~ Normal(mean=0.0, sd=1.0) + 10

// 🚫 can't apply log to a distribution object    
Real e ~ log(Normal(mean=0.0, sd=1.0))

// ✅ the function IID takes a distribution object and creates a new distribution object
Vector<Real> f ~ IID(
    base=Normal(mean=0.0, sd=1.0), 
    n=5
)
```

By convention, functions returning a distribution always start with an uppercase letter, whereas all others start with a lowercase letter.

We now look at more details, but we've already covered the main things to know.

!> Check out the [Core Component Library](components) for a current list of all types, distributions, and functions.

### Types

Every object has [one of many types](components).

#### Literals are associated with one or more types

A `"string"` is always of type `String`, `true` and `false` are always of type `Boolean`.

What about numbers? `10` could refer to a `PositiveInteger`, a `NonNegativeInteger`, a `Integer`, a `PositiveReal`, a `NonNegativeReal`, a `Real`, a `Rate`. `0.5` could also refer to a `Probability` among others. In these cases, the exact type is determined by its usage:

```phylospec
Real a = 10     // here, 10 is a Real
Real b = log(5) // log takes a PositiveReal, so 5 is a PositiveReal
```

#### Types can be aliased

One reason why PhyloSpec uses types is to make scripts more readable. One part of this is the use of aliases:

```phylospec
Rate birthRate ~ LogNormal(logMean=1, logSd=2)
Tree tree ~ Yule(birthRate)
```

If type `A` is an alias of type `B`, the two of them can be used interchangeably.

?> __Open question__: How far do we go with aliases? Do we use things like `Count` (for `NonNegativeInteger`), `Frequencies` (for `Simplex`), or `Path` and `TaxonName` (for `String`?).

#### Types can be parameterized

Every type can have one or more type parameters. Examples of parameterized types are `Vector<T>`, `Map<K,V>`, and `Sequence<T>`.

From the perspective of an object, its type parameter is *fixed upon generation*. The type of an object attribute can dependent on the type parameters:

```phylospec
Vector<Real> numbers = [0.5, 0.1]
Real last = numbers.first         // for Vector<T>, .first has type T
```

One might add *bounds* to a type parameter. However, we only ever interact with objects through generators. Hence, it is sufficient (and more flexible) to specify type parameter bounds there.

#### Types can extend from another type.

We have the luxury that we can define our type hierarchy from a purely conceptual perspective—we don't have to care (too much) about implementation details. A type `A` extends from a type `B` if *an object of type `B` is also an object of type `A`*. A `PositiveReal` is also a `Real`, a `TimeTree` is also a `Tree`.

An object of a subtype can always be used in places where a supertype is required:

```phylospec
PositiveReal a = 10
Real b = a  // this still works, as PositiveReal extends Real
```

?> __Open questions:__ Subtyping combined with generics raises the question of [covariance](https://web.archive.org/web/20150905085310/http://blogs.msdn.com/b/ericlippert/archive/2009/11/30/what-s-the-difference-between-covariance-and-assignment-compatibility.aspx): if `A` extends `B`, does `T<A>` extend `T<B>`? I think yes, but a more careful argument will follow.

### Function Calls

```phylospec
PositiveReal mean ~ Exponential(1.0);
Real y ~ Normal(mean=mean, sd=2.0);
Real y ~ Normal(mean=mean, sd=2.0, offset=1.0);
```

If there is only one argument, it can be passed directly (e.g., `Exponential(1.0)`). If there are multiple arguments, they must be named explicitly (e.g., `Normal(mean=1.0, sd=2.0)`).

There might be optional arguments. One can have multiple functions with the same name but different argument types. Functions with the same name cannot only differ in their return type.

?> __Open question:__ Do we allow default values?

### Nested Expressions

Nested expressions are allowed:

```phylospec
Real y ~ Normal(mean=log(100), sd=2.0);
```

is equivalent to:

```phylospec
Real mean = log(100);
Real y = Normal(mean=mean, sd=2.0);
```

Whereas

```phylospec
Real y ~ Normal(mean~Exponential(1.0), sd=2.0);
```

is equivalent to:

```phylospec
Real mean ~ Exponential(1.0);
Real y = Normal(mean=mean, sd=2.0);
```

### Vectorization

Instead of overly flexible loops, PhyloSpec supports list comprehension:

```phylospec
Vector<Real> x = [1.0, 2.0, 3.0]
Vector<Real> logX = [log(xi) for xi in x]
Vector<Real> xTimesLogX = [x * logX for x, logX in zip(first=x, second=logX)]
```

**Justification for list comprehensions:**
List comprehensions provide a declarative, mathematically-inspired syntax that naturally extends beyond simple function application. They excel at:
- Creating collections with complex expressions: `[xi^2 + yi for xi, yi in zip(x, y)]`
- Nested structures: `[[f(xi, yj) for yj in y] for xi in x]`
- Index-aware operations: `[i * xi for i, xi in enumerate(x)]`
- Conditional filtering: `[log(xi) for xi in x if xi > 0]`

This syntax aligns well with mathematical set-builder notation and provides more flexibility than map-style vectorization while remaining declarative and side-effect free - ideal for model specification languages where clarity and mathematical expressiveness are paramount.

### Distributions as Arguments

Distribution are normal objects produced by normal functions and can be assigned to variables and passed as arguments (distributions as first-class citizens):

```phylospec
// Create a vector of distribution objects
Vector<Distribution<Real>> components = [
    Normal(mean=0.0, sd=1.0),       // Normal is simply a function returning Distribution<Real>
    Normal(mean=5.0, sd=2.0),
    Normal(mean=10.0, sd=1.5)
];
Vector<Real> weights = [0.3, 0.5, 0.2];
Mixture mixture = Mixture(components=components, weights=weights);

// Or using list comprehension for programmatic creation
Vector<Distribution<Real>> components = [Normal(mean=i*2.0, sd=1.0) for i in 1:10];
Vector<Real> weights = repeat(x=0.1, rep=10);
Mixture mixture = Mixture(components=components, weights=weights);

// Sample from the mixture
Real x ~ mixture;
```

### Clamping

We use the decorator `@observedAs` to assign an observation to a random variable:

```phylospec
@observedAs(50)
Real x ~ Normal(mean=0.0, sd=1.0);
```

### Numerical Expressions

PhyloSpec supports standard numerical operations to enable mathematical transformations of variables. These operations are particularly useful when canonical distributions provide convenient priors, but transformed values are needed for downstream model components.

#### Syntax Rules

- Numerical expressions can be used anywhere functions can be used (right-hand side of `=` assignments)
- Numerical expressions cannot be mixed with random variable declarations (`~`) on the same line
- Standard operator precedence applies: `^` > `*,/` > `+,-`
- Parentheses can be used to override precedence

## Naming Conventions

- Types and functions returning distributions should use PascalCase (e.g., `PositiveReal`). Only alphanumeric characters are allowed, they must start with a letter.
- Variable names should use camelCase (e.g., `rate`) and can contain Unicode characters. They must start with a letter (a greek letter like `α` also works).
- Function not returning distributions should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.
- Function argument names should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.
- Attribute names should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.


## Engine-Specific Features

### Extensions

Engines can add custom types, distributions, and functions to the language. These extensions are namespaced and can be imported:

```phylospec
import revbayes.readAtlas;
import revbayes.RlAtlas;

RlAtlas atlas = readAtlas("atlas.csv");
```

Extensions are defined in *component libraries*. A [component library](specification) is a JSON file describing every additional types and functions.

### Engine Blocks

There can be engine-specific blocks that are not part of the language and can be used to configure the engine. _These blocks must not change the model specified and will be ignored by any other engine._

```phylospec
revbayes {
    // Configuration for RevBayes
    // these could be moves
}
```

?> __Open question:__ Is there a standardized syntax for engine blocks?

### Engine Decorators

- Statements can be decorated with engine-specific information. _These decorators must not change the model specified and will be ignored by any other engine._
- The syntax for the decorator arguments is identical to function call arguments.

```phylospec
@revbayes(someInternalArgument=true)
Real x ~ Normal(mean=0.0, sd=1.0);
```
