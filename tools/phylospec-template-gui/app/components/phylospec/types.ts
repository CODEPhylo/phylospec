import type { ZodType } from 'zod'

export type ComponentProps<T> = {
  value: T | null
  onChange: (value: T | null) => void
}

export type PhyloSpecComponent<T> = {
  id: string
  label: string
  outputType: string
  isLiteral: boolean
  schema: ZodType<T>
  Component: React.FC<ComponentProps<T>>
  toExpression: (value: T) => string
}

/** Registered by generator name; `outputType` is supplied from core-components.json at auto-registration time. */
export type PhyloSpecGeneratorComponent<T> = {
  generatorName: string
  id: string
  label: string
  schema: ZodType<T>
  Component: React.FC<ComponentProps<T>>
  toExpression: (value: T) => string
}
