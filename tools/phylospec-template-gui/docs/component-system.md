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
  isLiteral: boolean                  // drives TypeSelector mode bucketing
  schema: ZodType<T>
  Component: React.FC<ComponentProps<T>>
  toExpression: (value: T) => string  // converts value to a PhyloSpec expression fragment
}
```

`isLiteral` determines which TypeSelector mode the component appears under:

| `isLiteral` | Mode shown in | Assignment operator |
|---|---|---|
| `true` | Fixed | `=` |
| `false` (Distribution type) | Estimated | `~` |
| `false` (other type) | Calculated | `=` |

---

## Registry — `app/components/phylospec/registry.ts`

A module-level `Map<string, PhyloSpecComponent<unknown>[]>` keyed by `outputType`.

```ts
register(component: PhyloSpecComponent<T>): void
getComponents(type: string): PhyloSpecComponent<unknown>[]
```

Multiple components can be registered for the same type; `getComponents` returns them in registration order. Registrations are side effects that run on module import. `app/components/phylospec/index.ts` imports `./literal` and `./generator` to trigger them; any client component that imports from `app/components/phylospec` has the registry populated.

`resolveAlias` resolves both simple aliases (`Rate` → `PositiveReal`) and parameterised types (`Distribution<Rate>` → `Distribution<PositiveReal>`). `getComponents` automatically falls through to the resolved type when no direct registration exists.

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
   - The argument name (medium weight), a `=` or `~` operator badge, and an "(optional)" badge when `required: false`.
   - The argument description in small gray text.
   - A `TypeSelector` for the argument's type with `allowDistributions={true}`.

The operator badge reflects whether the current value for that argument is a prior distribution (`~`) or a deterministic value (`=`); it defaults to `=` when nothing is selected yet.

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
export type TypeSelectorValue = {
  componentId: string
  value: unknown
  isDistribution: boolean   // true when the selected component is from the Distribution<T> bucket
}

type TypeSelectorProps = {
  type: string
  value: TypeSelectorValue | null
  onChange: (v: TypeSelectorValue) => void
  allowDistributions?: boolean   // default false
}
```

### Modes

The selector groups components into up to three modes:

| Mode | Filter | Button label | Active colour |
|---|---|---|---|
| Fixed | `isLiteral === true` components for the given type | `= fixed` | Teal (accent) |
| Estimated | Components registered under `Distribution<GivenType>` | `~ estimated` | Cyan |
| Calculated | `isLiteral === false` components for the given type | `ƒ calculated` | Violet |

Only modes that have at least one registered component are shown. A **mode picker** (segmented button group) appears only when two or more modes are available.

### Basic behaviour

- When only one mode has components and that mode contains exactly one component, it is auto-selected — no picker UI is shown.
- When multiple components exist within the active mode, a button group lets the user choose one. No component is pre-selected when the mode picker is visible.
- Switching modes clears the current selection.
- Shows a gray placeholder when no components are registered for the type at all.

### Distribution mode (allowDistributions = true)

When `allowDistributions` is `true`, the selector also looks up components registered under `Distribution<{type}>` (e.g. `Distribution<PositiveReal>`). These appear under the **Estimated** mode.

`isDistribution` in the emitted `TypeSelectorValue` is `true` iff the selected component is from the `Distribution<T>` bucket.

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
  name?: string          // overrides the auto-derived tab/row label
  description?: string   // shown below the placeholder tab
}

type TabGroup = {
  name: string            // tab label
  description?: string    // optional summary shown at the top of the tab pane
  placeholders: string[]  // $placeholder keys that share this tab, in display order
}

type TemplateFormConfig = {
  experimentName?: string
  defaults?: Record<string, string>  // argument name → raw expression, e.g. { taxa: "taxa(data)" }
  placeholders: Record<string, PlaceholderConfig>
  groups?: TabGroup[]    // optional; placeholders not in any group each get their own tab
}

type TemplateFormProps = {
  template: string
  config: TemplateFormConfig
  onChange?: (resolvedTemplate: string) => void
}
```

### Behaviour

- Maintains `values: Record<string, TypeSelectorValue | null>` — one entry per placeholder.
- Calls `onChange` (if provided) with the resolved template whenever any value changes.
- Unresolved placeholders remain as their `$name` token in the output.
- Provides `DefaultsContext` (from `DefaultsContext.ts`) to all descendants, carrying `config.defaults ?? {}`.

### Resolution

For each placeholder, if a `TypeSelectorValue` is set, the matching component's `toExpression` is called and the token is replaced. When `TypeSelectorValue.value` is `null` — permanent for zero-arg generators like `jc69` — an empty record `{}` is passed to `toExpression`, producing `name()`.

### `defaults` — pre-filled generator arguments

`defaults` maps generator argument names to raw PhyloSpec expression strings. When a `GeneratorInput` renders an argument whose name appears in `defaults`:

- The argument row is not rendered — no user input is possible.
- The raw expression string is injected into the generated output verbatim (e.g. `taxa=taxa(data)`).
- The value is stored internally as a sentinel `TypeSelectorValue` with `componentId: '__default__'` and `value` equal to the raw expression string; `buildExpression` in `generator/index.ts` handles this sentinel.

### Layout

Two columns (`items-start`):

- **Left** (`flex-1`): a tab bar derived from `placeholders` and `groups`. Placeholders listed in a `TabGroup` share one tab (label = group name); all others each get their own tab. The active tab shows each placeholder's optional description and `TypeSelector` (with `allowDistributions={false}`) in declaration order.
- **Right** (`w-2/5 shrink-0`): a preview panel with a `<pre>` block showing the resolved template. Unresolved tokens are rendered in cyan; resolved values are rendered in the accent colour. A copy-to-clipboard button sits above the preview. An **Export model.phylospec** download button appears below — it is disabled until all placeholders are resolved.

## Global page styling

The GUI root page (`Demo.tsx`) includes a website-style top header with the PhyloSpec brand mark and navigation links. Global CSS (`app/globals.css`) sets `Noto Sans` as the default font, a base font size of `1.1rem`, and the accent variable `--accent: #15897d`, which is exposed as `text-accent` for headings and brand highlights.

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
  isLiteral: false,
  schema: z.number().positive(),
  Component: MyComponent,
  toExpression: (v) => String(v),
})
```

The `literal.*` and `generator.*` id namespaces are reserved; use `custom.*` or a descriptive prefix for hand-written additions.
