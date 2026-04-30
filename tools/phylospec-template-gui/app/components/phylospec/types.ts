import type { ZodType } from 'zod'

export type ComponentProps<T> = {
  value: T | null
  onChange: (value: T | null) => void
}

export type PhyloSpecComponent<T> = {
  id: string
  label: string
  outputType: string
  schema: ZodType<T>
  Component: React.FC<ComponentProps<T>>
  toExpression: (value: T) => string
}
