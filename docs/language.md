# Language

This document describes the philosophy and features of the PhyloSpec language.

## 1. Language Philosophy

- PhyloSpec is a language for describing phylogenetic models. It is _not_ a programming language.
- PhyloSpec aims to be _expressive_ enough to describe complex models, even if they cannot be efficiently inferred in current engines. However, its scope is explicitly restricted to _phylogenetic models_. Information like proposal distributions or the specific inference algorithm are not part of the core language (see 3.2 for ways to specify these).
- PhyloSpec is designed to be _human-readable_. It should be concise and easy to understand.
- PhyloSpec is designed to prevent _invalid models_. Types and other language features are used to detect invalid models before inference.
- PhyloSpec is designed to be _extensible_. Engines can add custom types, distributions, and functions to the language.

## 2. Language Features

### 2.1. General Syntax

```
// this is a comment

PositiveReal rate = 2.0;
PositiveReal draw ~ Exponential(rate);
Real logDraw = log(draw);

Alignment alignment = readNexus("file.nex");
Int numTaxa = alignment.numTaxa()
```

- Every statement describes an assignment of an expression to a variable (`=`) or assignment of a distribution to a random variable (`~`).
- Every (random) variable is assigned a type.
- There are five types of expressions:
  - Literals (e.g., `2.0`, `"Hallo"`, or `[1.0, 2.0, 3.0]`)
  - Variable references (e.g., `rate`)
  - Distributions (e.g., `Exponential(rate)`)
  - Function calls (e.g., `log(draw)`)
  - Method calls (e.g., `alignment.numTaxa()`)
- All five types of expressions can be used in variable assignments using `=`, while only distributions can be used in random variable assignments using `~`.
- Comments start with `//` and continue until the end of the line.

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
> - Do we allow JS-style syntax when passing a variable with the same name as the argument (e.g., `Exponential(rate)` instead of `Exponential(rate=rate)`)?

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
Vec<Real> x = [1.0, 2.0, 3.0];
Vec<Real> logX = log(x);

// Explicit vectorization where the vectorized argument is highlighted
Vec<Real> logX = log(x...);

// Julia and MATLAB-style explicit vectorization
Vec<Real> logX = log.(x);

// R and python-style explicit vectorization (only works with one argument)
Vec<Real> logX = map(log, x);
```

> [!NOTE]
> Open questions:
>
> - What version of vectorization should we use?

### 2.6. Distributions as Arguments

Distributions can be assigned to variables and passed as arguments (distributions as first-class citizens):

```
Vec<Real> components ~ IID(dist=Normal(mean=0.0, sd=1.0), rep=10);
Vec<Real> weights = repeat(x=0.1, rep=10);
Mixture mixture = Mixture(components=components, weights=weights);
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
