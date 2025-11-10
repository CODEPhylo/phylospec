# Language

This document describes the philosophy and features of the PhyloSpec language.

## 1. Language Philosophy

- PhyloSpec is a language for describing phylogenetic models. It is _not_ a programming language.
- PhyloSpec aims to be expressive enough to describe complex models, even if they cannot be efficiently inferred in current engines. However, its scope is explicitly restricted to _phylogenetic models_. Things like proposal distributions or the specific inference algorithm are not part of the language (see 3.2 for ways to specify these).
- PhyloSpec is designed to be _human-readable_. It should be concise and easy to understand.
- PhyloSpec is designed to be _declarative_. Models should describe _what_ the relationships are, not _how_ to compute them. This promotes clarity, prevents side effects, and aligns with mathematical notation.
- PhyloSpec is designed to prevent _invalid models_. Types and other language features are used to detect invalid models before inference.
- PhyloSpec is designed to be _extensible_. Engines can add custom types, distributions, and functions to the language.

## 2. Language Features

This is an example showing the features of the language:

```
// Basic HKY model with Yule tree prior
// This example specifies an HKY substitution model with a Yule tree prior

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
Alignment sequences ~ PhyloCTMC(tree=phylogeny, Q=substModel, numSequences=observedSequences.ntax)
```

### 2.1. General Syntax

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
Real b=log(a)

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

> [!TIP]
> Check out the [types.md](types.md), [distributions.md](distributions.md), and [functions.md](functions.md) documents for a list of all types, distributions, and functions.

### 2.2. Naming Conventions

- Types and functions returning distributions should use PascalCase (e.g., `PositiveReal`). Only alphanumeric characters are allowed, they must start with a letter.
- Variable names should use camelCase (e.g., `rate`) and can contain Unicode characters. They must start with a letter (a greek letter like `α` also works).
- Function not returning distributions should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.
- Function argument names should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.
- Attribute names should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.

### 2.3. Types

Every object has [one of many types](./types.md).

#### Literals are associated with one or more types

A `"string"` is always of type `String`, `true` and `false` are always of type `Boolean`.

What about numbers? `10` could refer to a `PositiveInteger`, a `NonNegativeInteger`, a `Integer`, a `PositiveReal`, a `NonNegativeReal`, a `Real`, a `Rate`. `0.5` could also refer to a `Probability` among others. In these cases, the exact type is determined by its usage:

```
Real a = 10     // here, 10 is a Real
Real b = log(5) // log takes a PositiveReal, so 5 is a PositiveReal
```

#### Types can be aliased

One reason why PhyloSpec uses types is to make scripts more readable. One part of this is the use of aliases:

```
Rate birthRate ~ LogNormal(logMean=1, logSd=2)
Tree tree ~ Yule(birthRate)
```

If type `A` is an alias of type `B`, the two of them can be used interchangeably.

> [!NOTE]
> Open questions:
>
> How far do we go with aliases? Do we use things like `Count` (for `NonNegativeInteger`), `Frequencies` (for `Simplex`), or `Path` and `TaxonName` (for `String`?).

#### Types can be parameterized

Every type can have one or more type parameters. Examples of parameterized types are `Vector<T>`, `Map<K,V>`, and `Sequence<T>`. The type of an object attribute can dependent on the type parameters:

```
Vector<Real> numbers = [0.5, 0.1]
Real last = numbers.first         // for Vector<T>, .first has type T
```

From the perspective of an object, its type parameter is *fixed upon generation*.

One might add *bounds* to a type parameter. However, we only ever interact with objects through generators. Hence, it is sufficient (and more flexible) to specify type parameter bounds there.

#### Types can extend from another type.

We have the luxury that we can define our type hierarchy from a purely conceptual perspective—we don't have to care (too much) about implementation details. A type `A` extends from a type `B` if *an object of type `B` is also an object of type `A`*. A `PositiveReal` is also a `Real`, a `TimeTree` is also a `Tree`.

An object of a subtype can always be used in places where a supertype is required:

```
PositiveReal a = 10
Real b = a  // this still works, as PositiveReal extends Real
```

> [!NOTE]
> Open questions:
>
> - Subtyping combined with generics raises the question of [covariance](https://web.archive.org/web/20150905085310/http://blogs.msdn.com/b/ericlippert/archive/2009/11/30/what-s-the-difference-between-covariance-and-assignment-compatibility.aspx): if `A` extends `B`, does `T<A>` extend `T<B>`? I think yes, but a more careful argument will follow.

### 2.4. Function Calls

```
PositiveReal mean ~ Exponential(1.0);
Real y ~ Normal(mean=mean, sd=2.0);
Real y ~ Normal(mean=mean, sd=2.0, offset=1.0);
```

- If there is only one argument, it can be passed directly (e.g., `Exponential(1.0)`).
- If there are multiple arguments, they must be named explicitly (e.g., `Normal(mean=1.0, sd=2.0)`).
- There might be optional arguments.
- There might be multiple functions with the same name but different argument types. Functions with the same name cannot only differ in their return type.

> [!NOTE]
> Open questions:
>
> - Do we allow default values?

### 2.5. Nested Expressions

Nested expressions are allowed:

```
Real y ~ Normal(mean=log(100), sd=2.0);
```

is equivalent to:

```
Real mean = log(100);
Real y = Normal(mean=mean, sd=2.0);
```

Whereas

```
Real y ~ Normal(mean~Exponential(1.0), sd=2.0);
```

is equivalent to:

```
Real mean ~ Exponential(1.0);
Real y = Normal(mean=mean, sd=2.0);
```

### 2.6. Vectorization

Instead of overly flexible loops, vectorization is used. There are multiple proposed syntaxes for vectorization:
```
// LPhy-style implicit vectorization
Vector<Real> x = [1.0, 2.0, 3.0];
Vector<Real> logX = log(x);
// Explicit vectorization where the vectorized argument is highlighted
Vector<Real> logX = log(x...);
// Julia and MATLAB-style explicit vectorization
Vector<Real> logX = log.(x);
// R and python-style explicit vectorization (only works with one argument)
Vector<Real> logX = map(log, x);
// List comprehension style
Vector<Real> logX = [log(xi) for xi in x];
```

**Justification for list comprehensions:**
List comprehensions provide a declarative, mathematically-inspired syntax that naturally extends beyond simple function application. They excel at:
- Creating collections with complex expressions: `[xi^2 + yi for xi, yi in zip(x, y)]`
- Conditional filtering: `[log(xi) for xi in x if xi > 0]`
- Index-aware operations: `[i * xi for i, xi in enumerate(x)]`
- Nested structures: `[[f(xi, yj) for yj in y] for xi in x]`

This syntax aligns well with mathematical set-builder notation and provides more flexibility than map-style vectorization while remaining declarative and side-effect free - ideal for model specification languages where clarity and mathematical expressiveness are paramount.


> [!NOTE]
> Open questions:
>
> - What version of vectorization should we use?

### 2.7. Distributions as Arguments

Distribution are normal objects produced by normal functions and can be assigned to variables and passed as arguments (distributions as first-class citizens):
```
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

### 2.8. Clamping

We use the decorator `@observedAs` to assign an observation to a random variable:

```
@observedAs(50)
Real x ~ Normal(mean=0.0, sd=1.0);
```

### 2.9. Blocks

Blocks could be used to group statements:

```
data {
    Real x = 100;
}

model {
    Real y ~ Normal(mean=x, sd=1.0);
}
```

> [!NOTE]
> Open questions:
>
> - Should blocks be used?

### 2.10. Numerical Expressions

PhyloSpec supports standard numerical operations to enable mathematical transformations of variables. These operations are particularly useful when canonical distributions provide convenient priors, but transformed values are needed for downstream model components.

#### Supported Operations

- **Arithmetic**: `+`, `-`, `*`, `/` (division), `^` (exponentiation)
- **Element-wise operations**: When applied to vectors, operations are performed element-wise
- **Scalar-vector operations**: Scalars are broadcast to match vector dimensions

#### Syntax Rules

- Numerical expressions can be used anywhere functions can be used (right-hand side of `=` assignments)
- Numerical expressions cannot be mixed with random variable declarations (`~`) on the same line
- Standard operator precedence applies: `^` > `*,/` > `+,-`
- Parentheses can be used to override precedence

#### Examples

**Example 1: Dirichlet with weighted transformation**
```
// Define weights and concentration parameters
Vector<PositiveReal> weights = [2.0, 3.0, 1.0, 4.0];
Vector<PositiveReal> alpha = [1, 1, 1, 1];

// Sample from Dirichlet with weighted concentration
Simplex unr ~ Dirichlet(alpha=alpha * weights);

// Transform to get weighted rates
Vector<Real> r = unr * weights / sum(weights);
```

In this example, the Dirichlet distribution provides a convenient prior over simplex values, but the model requires weighted rates. The transformation `r = unr * weights / sum(weights)` converts the raw simplex values to the needed parameterization.
Concretely this is needed for relative rates of partitions of different sizes (weights).

**Example 2: Multinomial with offset transformation**
```
// Model parameters
PositiveInteger k = 10;
PositiveInteger numTaxa = 100;

// Sample counts from multinomial
Vector<NonNegativeInteger> x ~ Multinomial(p=repeat(1/k, k), n=numTaxa-k);

// Transform to positive integers by adding 1
Vector<PositiveInteger> y = x + 1;
```

Here, the Multinomial distribution naturally produces counts including zeros, but downstream components may require strictly positive integers. The simple transformation `y = x + 1` ensures all values are positive while maintaining the stochastic structure from the prior.
Concretely this is needed for group sizes in the skyline model, which must sum to numTaxa-1 and must all be positive.

#### Design Rationale

Separating random variable declarations from deterministic transformations:
- Maintains clarity about which variables are stochastic vs. deterministic
- Preserves the declarative nature of distribution statements
- Makes the computational graph explicit for inference engines
- Allows type checking to ensure operations are valid

> [!NOTE]
> Open questions:
> - Should we support additional operations like modulo (`%`) or integer division (`//`)?
> - Should we allow more complex expressions like `x[i] + y[j]` with indexing?
> - How do we handle type promotion in mixed-type operations (e.g., `Integer + Real`)?

### 3. Engine-Specific Features

#### 3.1. Extensions

Engines can add custom types, distributions, and functions to the language. These extensions are namespaced and can be imported:

```
import revbayes.readAtlas;
import revbayes.RlAtlas;

RlAtlas atlas = readAtlas("atlas.csv");
```

Extensions are defined in *component libraries*. A component library is a JSON file describing every additional types and functions. Check out the [JSON schema](../schema/component-library.schema.json) and the [core component library](../schema/phylospec-core-component-library.json) to see how this looks like.

#### 3.2. Engine Blocks

There can be engine-specific blocks that are not part of the language and can be used to configure the engine. _These blocks must not change the model specified and will be ignored by any other engine._

```
revbayes {
    // Configuration for RevBayes
    // these could be moves
}
```

> [!NOTE]
> Open questions:
>
> - Is there a standardized syntax for engine blocks?

#### 3.3. Engine Decorators

- Statements can be decorated with engine-specific information. _These decorators must not change the model specified and will be ignored by any other engine._
- The syntax for the decorator arguments is identical to function call arguments.

```
@revbayes(someInternalArgument=true)
Real x ~ Normal(mean=0.0, sd=1.0);
```
