# PhyloSpec Component System

## Goal

The GUI lets users compose PhyloSpec models visually. Every UI component in this system corresponds to one PhyloSpec expression that produces a value of a specific type (e.g. `PositiveReal`, `String`, `Tree`). Components can represent:

1. **Literals** — a typed input box (e.g. a number field for `PositiveReal`)
2. **A type selector** — picks any registered component for a given type and renders it
3. **Generator components** — inputs for a generator's arguments, auto-composed from the above
4. **Specialised generator components** — hand-written for a nicer UI (future work)

The output of every component is a fragment of a PhyloSpec model, not a runtime value.

Check out 'app/core-components.json' for a list of all types and generators.

---

## Core types — `app/components/phylospec/types.ts`

### `ComponentProps<T>`

```ts
type ComponentProps<T> = {
  value: T | null          // null = no valid value entered yet
  onChange: (value: T | null) => void
}
```

`null` flows up whenever the raw input is empty or fails Zod validation. Parent components treat `null` as "unset".

### `PhyloSpecComponent<T>`

```ts
type PhyloSpecComponent<T> = {
  id: string                          // unique, e.g. "literal.positiveReal"
  label: string                       // shown in the TypeSelector button group
  outputType: string                  // PhyloSpec type name, e.g. "PositiveReal"
  schema: ZodType<T>                  // Zod schema used for validation
  Component: React.FC<ComponentProps<T>>
  toExpression: (value: T) => string  // converts value to a PhyloSpec expression fragment
}
```

---

## Registry — `app/components/phylospec/registry.ts`

A module-level `Map<string, PhyloSpecComponent<unknown>[]>` acts as the registry. It is a plain singleton — no React context needed.

```ts
register(component: PhyloSpecComponent<T>): void
getComponents(type: string): PhyloSpecComponent<unknown>[]
```

Multiple components can be registered for the same type. `getComponents` returns them in registration order.

**Important:** registrations are side effects that run when the module is imported. The top-level `app/components/phylospec/index.ts` does `import './literal'` to trigger all literal registrations. Any client component that imports from `app/components/phylospec` will have the registry populated.

---

## Literal components — `app/components/phylospec/literal/`

Literal components render a single input box wired to a Zod schema.

### Editing pattern

All numeric inputs keep a local `raw: string` state separate from the validated `value` prop:

- Every keystroke updates `raw` immediately (no blocked input).
- `onChange` is called with the parsed value only when Zod validation passes; otherwise `null` is emitted.
- When the parent resets `value` to `null` (e.g. after a component switch), a `useEffect` clears `raw`.

### Border colours

| State | Border |
|---|---|
| Empty / incomplete | Gray |
| Valid | Green |
| Invalid | Red |

### `IntegerInput`

`type="number" step="1"`. Parses with `parseInt`. Treats `""` and `"-"` as incomplete (no error shown).

### `RealInput`

`type="number" step="any"`. Parses with `parseFloat`. Treats `""`, `"-"`, `"."`, and `"-."` as incomplete.

### `StringInput`

`type="text"`. Empty string emits `null`; any non-empty string is valid under the default `z.string()` schema.

---

## Literal registrations — `app/components/phylospec/literal/index.ts`

Each type gets exactly one registration. Aliases use the schema of their parent type:

| Type | Schema | Notes |
|---|---|---|
| `Integer` | `z.number().int()` | |
| `PositiveInteger` | `z.number().int().positive()` | min=1 |
| `NonNegativeInteger` | `z.number().int().nonnegative()` | min=0 |
| `Count` | `z.number().int().nonnegative()` | alias of NonNegativeInteger |
| `Real` | `z.number()` | |
| `PositiveReal` | `z.number().positive()` | |
| `NonNegativeReal` | `z.number().nonnegative()` | |
| `Probability` | `z.number().min(0).max(1)` | |
| `Rate` | `z.number().positive()` | alias of PositiveReal |
| `Age` | `z.number().nonnegative()` | alias of NonNegativeReal |
| `String` | `z.string()` | `toExpression` wraps value in `"..."` |

---

## Generator components — `app/components/phylospec/generator/`

Generator components cover every generator declared in `app/core-components.json`. They are registered automatically at module load time — no manual `register()` calls are needed when a new generator is added to the JSON.

### `GeneratorInput` — `generator/GeneratorInput.tsx`

A generic React component that renders one TypeSelector per argument, stacked vertically:

```ts
type GeneratorArg = {
  name: string
  type: string
  description: string
  required: boolean
  default?: unknown
}

type GeneratorInputValue = Record<string, TypeSelectorValue | null>
```

Each argument is rendered inside a bordered rounded box (`bg-gray-50/50 border border-gray-200 rounded-lg`). The box contains:
- The argument name (bold) with an "(optional)" badge when `required: false`
- The description in small gray text
- A `TypeSelector` for the argument's type

When an argument changes, `onChange` is called with the full updated record (partial values for unfilled arguments remain as `null`).

### Auto-registration — `generator/index.ts`

On import, iterates over all entries in `core-components.json → componentLibrary.generators`. When the same generator name appears more than once (e.g. `fromNexus`, `BirthDeath`), only the **first** occurrence is registered.

Each generator is registered as:

| Field | Value |
|---|---|
| `id` | `generator.<name>` |
| `label` | the generator name |
| `outputType` | `generatedType` from the JSON |
| `schema` | `z.record(z.string(), z.any())` (cast to `ZodType<GeneratorInputValue>`) |
| `Component` | `GeneratorInput` with `args` closed over |
| `toExpression` | see below |

### `toExpression` format

Arguments are emitted on separate lines, named, following the multi-argument convention from the PhyloSpec language spec. Arguments with a `null` value or no registered component for their type are omitted:

```
Normal(
    mean=0.0,
    sd=1.0
)
```

For generators with no arguments or when all arguments are unset: `jc69()`.

Each argument's expression fragment is obtained by calling `toExpression` on the matching registered component for that argument's type.

---

## TypeSelector — `app/components/phylospec/TypeSelector.tsx`

```ts
type TypeSelectorProps = {
  type: string
  value: TypeSelectorValue | null    // { componentId, value }
  onChange: (v: TypeSelectorValue) => void
}
```

Behaviour:
- Calls `getComponents(type)` to find all registered components.
- If **one** is registered, renders it directly with no selector UI.
- If **multiple** are registered, shows a button group; no component is pre-selected — the user must make an explicit choice.
- Switching components resets the value to `null`.
- Shows a gray placeholder when no components are registered for the type.

---

## Entry point — `app/components/phylospec/index.ts`

```ts
import './literal'     // triggers literal register() side-effects
import './generator'   // triggers generator register() side-effects

export { register, getComponents } from './registry'
export { TypeSelector } from './TypeSelector'
export type { TypeSelectorValue } from './TypeSelector'
export type { PhyloSpecComponent, ComponentProps } from './types'
```

---

## Adding a new component

Call `register()` with a `PhyloSpecComponent<T>` from any module that gets imported before the component is used:

```ts
import { z } from 'zod'
import { register } from '@/app/components/phylospec/registry'

register({
  id: 'myNamespace.myComponent',
  label: 'My component',
  outputType: 'PositiveReal',
  schema: z.number().positive(),
  Component: MyComponent,
  toExpression: (v) => String(v),
})
```

The `id` should be namespaced to avoid collisions. The `literal.*` and `generator.*` namespaces are already in use; prefer `custom.*` or a descriptive prefix for hand-written additions.
