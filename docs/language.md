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

### 2.1. General Syntax

PhyloSpec models describe a graphical model. Every statement corresponds to one variable in the graph.

The simplest statements are constant assignments:

```phylospec
Rate birthRate = 2.5
```

This line defines a new variable called `birtRate` of type `Rate` with a constant value of `2.5`. Every variable has a type. Whenever possible, PhyloSpec uses types that tell you what a variable actually represents. This is why `birthRate` has type `Rate` and not just `PositiveReal`.

We can apply functions and the usual numerical expressions:

```phylospec
Rate deathRate = 0.5 * birthRate
Rate diversificationRate = birthRate - deathRate
Real logDiversificationRate = log(diversificationRate)
```

Besides numbers, strings (`"text"`), and vectors (`[1, 2]`), functions can create more sophisticated types:

```phylospec
Alignment alignment = nexus(file="sequences.nex")
Count numTaxa = alignment.numTaxa
TaxonSet taxa = alignment.taxa
```

So far, we only have determinstic variables. Let's change that!

```phylospec
Distribution<Real> normalDistribution = Normal(mean = 0.0, sd = 1.0)
Real drawnValue ~ normalDistribution
```

Here, `Normal` is a function which returns a `Distribution<Real>` object (a distribution on real numbers). This distribution is then assigned to the *random variable* `drawnValue` using the `~` operator. This is where the randomness in our model comes from!

Use the `=` operator for assignments of constant values or for determinstic transformations of random variables. `~` assigns a distribution to a random variable, hence it always has to be preceded by a `Distribution` object.

Some examples of valid and invalid statements:

```phylospec
// ✅
Real a ~ Exponential(rate = 1.0)

// ✅
Real b = log(a)

// 🚫 b is not a distribution
Real c ~ b                          

// 🚫 can't add a number to a distribution object
Real d ~ Normal(mean=0.0, sd=1.0) + 10

// 🚫 can't apply log to a distribution object    
Real e ~ log(Normal(mean=0.0, sd=1.0))

// ✅ the function IID takes a distribution object and creates a new distribution object
//    hence we can draw from it
Vector<Real> f ~ IID(
    base = Normal(mean=0.0, sd=1.0), 
    n=5
)
```

> [!TIP]
> Check out the [types.md](types.md), [distributions.md](distributions.md), and [functions.md](functions.md) documents for a list of all types, distributions, and functions.

> [!NOTE]
> Open questions:
>
> - Do we use `;` or newlines to separate statements?
> - Do we allow numerical operations like addition and multiplication?

### 2.2. Naming Conventions

- Types and distributions names should use PascalCase (e.g., `PositiveReal`). Only alphanumeric characters are allowed, they must start with a letter.
- Variable names should use camelCase (e.g., `rate`) and can contain Unicode characters. They must start with a letter.
- Function names should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.
- Function and distribution argument names should use camelCase (e.g., `log`). Only alphanumeric characters are allowed, they must start with a letter.
- Redundant prefixes like `dn` or suffixes like `Distribution` are discouraged.

### 2.3. Function, Method and Distribution Calls

```
PositiveReal mean ~ Exponential(1.0);
Real y ~ Normal(mean=mean, sd=2.0);
```

- If there is only one argument, it can be passed directly (e.g., `Exponential(1.0)`).
- If there are multiple arguments, they must be named explicitly (e.g., `Normal(mean=1.0, sd=2.0)`).

> [!NOTE]
> Open questions:
>
> - Do we allow optional arguments and default values?
> - Do we use JSON schemas for more fine-grained definition of valid arguments, or do we use overloading?
> - Do we allow JS-style syntax when passing a variable with the same name as the argument (e.g., `Normal(mean, sd)` instead of `Normal(mean=mean, sd=sd)`)?

### 2.4. Nested Expressions

Nested expressions are allowed and are equivalent to `=` assignments:

```
PositiveReal y ~ Normal(mean=log(100), sd=2.0);
```

is equivalent to:

```
Real mean = log(100);
Real y = Normal(mean=mean, sd=2.0);
```

> [!NOTE]
> Open questions:
>
> - Do we allow inline draws (e.g., `Real y = Normal(mean~Exponential(1.0), sd=log(~Exponential(1.0)))`)?

### 2.5. Vectorization
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

### 2.6. Distributions as Arguments
Distributions can be assigned to variables and passed as arguments (distributions as first-class citizens):
```
// Create a vector of distribution objects
Vector<Distribution> components = [
    Normal(mean=0.0, sd=1.0),
    Normal(mean=5.0, sd=2.0),
    Normal(mean=10.0, sd=1.5)
];
Vector<Real> weights = [0.3, 0.5, 0.2];
Mixture mixture = Mixture(components=components, weights=weights);

// Or using list comprehension for programmatic creation
Vector<Distribution> components = [Normal(mean=i*2.0, sd=1.0) for i in 1:10];
Vector<Real> weights = repeat(x=0.1, rep=10);
Mixture mixture = Mixture(components=components, weights=weights);

// Sample from the mixture
Real x ~ mixture;
```

### 2.7. Clamping

Clamping is used to assign an observation to a random variable. There are multiple proposed syntaxes for clamping:

```
// Rev-style function clamping
Real x ~ Normal(mean=0.0, sd=1.0);
x.clamp(50);

// BEASTLang-style decorator clamping
@observed(50)
Real x ~ Normal(mean=0.0, sd=1.0);
```

The decorator approach is more declarative - it describes _what_ x is (an observed value) rather than _how_ to make it observed (through a method call). This aligns better with the language philosophy.

> [!NOTE]
> Open questions:
>
> - What version of clamping should we use?

### 2.8. Blocks

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

### 2.9. Numerical Expressions

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

- Function and distribution calls can be decorated with engine-specific information. _These decorators must not change the model specified and will be ignored by any other engine._
- The syntax for the decorator arguments is identical to function call arguments.

```
@revbayes(someInternalArgument=true)
Real x ~ Normal(mean=0.0, sd=1.0);
```

> [!NOTE]
> Open questions:
>
> - How do these decorators handle multiple function calls on one line?
