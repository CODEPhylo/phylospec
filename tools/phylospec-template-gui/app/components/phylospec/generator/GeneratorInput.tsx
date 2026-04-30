'use client'

import { useContext, useEffect, useMemo } from 'react'
import { TypeSelector, TypeSelectorValue } from '../TypeSelector'
import { DefaultsContext } from '../DefaultsContext'
import { DEFAULT_COMPONENT_ID } from './index'

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
  const defaults = useContext(DefaultsContext)

  const sentinelDefaults = useMemo(
    () => Object.fromEntries(
      args
        .filter((arg) => arg.name in defaults)
        .map((arg) => [arg.name, { componentId: DEFAULT_COMPONENT_ID, value: defaults[arg.name], isDistribution: false }])
    ),
    [args, defaults]
  )

  useEffect(() => {
    if (Object.keys(sentinelDefaults).length === 0) return
    const needsUpdate = Object.entries(sentinelDefaults).some(
      ([k, v]) => value?.[k]?.componentId !== DEFAULT_COMPONENT_ID || value?.[k]?.value !== v.value
    )
    if (needsUpdate) onChange({ ...(value ?? {}), ...sentinelDefaults })
  }, [sentinelDefaults])

  const visibleArgs = args.filter((arg) => !(arg.name in defaults))

  if (visibleArgs.length === 0 && !description) return null

  function handleArgChange(argName: string, v: TypeSelectorValue) {
    onChange({ ...(value ?? {}), ...sentinelDefaults, [argName]: v })
  }

  return (
    <div className="flex flex-col gap-4">
      {description && (
        <p className="text-sm text-gray-600 dark:text-gray-400">{description}</p>
      )}
      {visibleArgs.map((arg) => {
        const argVal = value?.[arg.name] ?? null
        const operator = argVal ? (argVal.isDistribution ? '~' : '=') : '='
        return (
          <div key={arg.name} className="flex flex-col gap-1 rounded-lg bg-gray-50/50 border border-gray-200 p-3 dark:bg-gray-800/60">
            <div className="flex items-center gap-1">
              <span className="text-sm font-medium">{arg.name}</span>
              <span className="text-sm text-gray-500">{operator}</span>
              {!arg.required && <span className="text-sm text-gray-500">(optional)</span>}
            </div>
            <span className="text-sm text-gray-600">{arg.description}</span>
            <TypeSelector
              type={arg.type}
              value={argVal}
              onChange={(v) => handleArgChange(arg.name, v)}
              allowDistributions={true}
            />
          </div>
        )
      })}
    </div>
  )
}
