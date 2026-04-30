'use client'

import { TypeSelector, TypeSelectorValue } from '../TypeSelector'

export type GeneratorArg = {
  name: string
  type: string
  description: string
  required: boolean
  default?: unknown
}

export type GeneratorInputValue = Record<string, TypeSelectorValue | null>

type GeneratorInputProps = {
  value: GeneratorInputValue | null
  onChange: (value: GeneratorInputValue | null) => void
  args: GeneratorArg[]
  description?: string
}

export function GeneratorInput({ value, onChange, args, description }: GeneratorInputProps) {
  if (args.length === 0 && !description) return null

  function handleArgChange(argName: string, v: TypeSelectorValue) {
    onChange({ ...(value ?? {}), [argName]: v })
  }

  return (
    <div className="flex flex-col gap-4">
      {description && (
        <p className="text-xs text-gray-500 dark:text-gray-400">{description}</p>
      )}
      {args.map((arg) => (
        <div key={arg.name} className="flex flex-col gap-1 rounded-lg bg-gray-50/50 border border-gray-200 p-3 dark:bg-gray-800/60">
          <div className="flex items-center gap-1">
            <span className="text-xs font-medium">{arg.name}</span>
            {!arg.required && <span className="text-xs text-gray-400">(optional)</span>}
          </div>
          <span className="text-xs text-gray-500">{arg.description}</span>
          <TypeSelector
            type={arg.type}
            value={value?.[arg.name] ?? null}
            onChange={(v) => handleArgChange(arg.name, v)}
          />
        </div>
      ))}
    </div>
  )
}
