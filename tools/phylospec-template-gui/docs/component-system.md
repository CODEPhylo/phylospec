# PhyloSpec Component System

## Goal

The GUI lets users compose PhyloSpec models visually. Every UI component corresponds to one PhyloSpec expression that produces a value of a specific type (e.g. `PositiveReal`, `String`, `Tree`). Components can represent:

1. **Literals** — a typed input box (e.g. a number field for `PositiveReal`)
2. **Generators** — inputs for a generator's arguments, auto-composed from literals
3. **Specialised generators** — hand-written for a nicer UI (future work)

The output of every component is a fragment of a PhyloSpec model, not a runtime value.

See `app/core-components.json` for all types and generators.

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
  schema: ZodType<T>
  Component: React.FC<ComponentProps<T>>
  toExpression: (value: T) => string  // converts value to a PhyloSpec expression fragment
}
```

---

## Registry — `app/components/phylospec/registry.ts`

A module-level `Map<string, PhyloSpecComponent<unknown>[]>` keyed by `outputType`.

```ts
register(component: PhyloSpecComponent<T>): void
getComponents(type: string): PhyloSpecComponent<unknown>[]
```

Multiple components can be registered for the same type; `getComponents` returns them in registration order. Registrations are side effects that run on module import. `app/components/phylospec/index.ts` imports `./literal` and `./generator` to trigger them; any client component that imports from `app/components/phylospec` has the registry populated.

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

Each type gets exactly one registration. Aliases share the schema of their parent type:

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

Generator components cover every generator declared in `app/core-components.json` and are registered automatically at module load — no manual `register()` call is needed when a generator is added to the JSON.

### `GeneratorInput` — `generator/GeneratorInput.tsx`

Props:

```ts
type GeneratorArg = {
  name: string
  type: string
  description: string
  required: boolean
  default?: unknown
}

type GeneratorInputProps = {
  value: GeneratorInputValue | null   // Record<string, TypeSelectorValue | null>
  onChange: (value: GeneratorInputValue | null) => void
  args: GeneratorArg[]
  description?: string                // generator-level description, shown above args
}
```

Renders nothing when both `args` is empty and `description` is absent. Otherwise renders a column containing:

1. An optional `<p>` with the generator description (small gray text), if provided.
2. One bordered rounded box per argument, each containing:
   - The argument name (medium weight) with an "(optional)" badge when `required: false`.
   - The argument description in small gray text.
   - A `TypeSelector` for the argument's type.

When any argument changes, `onChange` is called with the full updated record; unfilled arguments remain `null`.

### Auto-registration — `generator/index.ts`

On import, iterates over `core-components.json → componentLibrary.generators`. When the same generator name appears more than once, only the first occurrence is registered.

| Field | Value |
|---|---|
| `id` | `generator.<name>` |
| `label` | the generator name |
| `outputType` | `generatedType` from the JSON |
| `schema` | `z.record(z.string(), z.any())` cast to `ZodType<GeneratorInputValue>` |
| `Component` | `GeneratorInput` with `args` and `description` closed over |
| `toExpression` | see below |

### `toExpression` format

Arguments are emitted on separate indented lines. Arguments whose value is `null` or whose type has no registered component are omitted:

```
Normal(
    mean=0.0,
    sd=1.0
)
```

When no arguments are set: `Normal()`.

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
- If **one** is registered, it is auto-selected — the component renders immediately with no selector UI.
- If **multiple** are registered, shows a button group. The user must click a button to select one; no component is pre-selected.
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

## TemplateForm — `app/components/phylospec/TemplateForm.tsx`

Renders a form from a PhyloSpec template string and a config that maps `$placeholder` tokens to their types.

```ts
type PlaceholderConfig = {
  type: string
  description?: string   // shown below the placeholder tab
}

type TemplateFormProps = {
  template: string
  config: Record<string, PlaceholderConfig>  // keys include "$", e.g. "$tree"
  onChange?: (resolvedTemplate: string) => void
}
```

### Behaviour

- Maintains `values: Record<string, TypeSelectorValue | null>` — one entry per placeholder.
- Calls `onChange` (if provided) with the resolved template whenever any value changes.
- Unresolved placeholders remain as their `$name` token in the output.

### Resolution

For each placeholder, if a `TypeSelectorValue` is set, the matching component's `toExpression` is called and the token is replaced via `String.replaceAll`. When `TypeSelectorValue.value` is `null` — permanent for zero-arg generators like `jc69` — an empty record `{}` is passed to `toExpression`, producing `name()`.

### Layout

Two columns (`items-start`):

- **Left** (`flex-1`): a tab bar with one tab per placeholder (label = name without leading `$`). The active tab shows an optional description and a `TypeSelector`.
- **Right** (`w-1/3 shrink-0`): a `<pre>` block showing the resolved template with unresolved tokens left in place.

---

## Adding a new component

Call `register()` from any module that is imported before the component is used:

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

The `literal.*` and `generator.*` id namespaces are reserved; use `custom.*` or a descriptive prefix for hand-written additions.
