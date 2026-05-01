'use client'

import { useContext, useEffect, useMemo } from 'react'
import { TypeSelector, TypeSelectorValue } from '../TypeSelector'
import { DefaultsContext } from '../DefaultsContext'
import { DEFAULT_COMPONENT_ID } from './constants'

export type GeneratorArg = {
  name: string
  type: string
  description: string
  required: boolean
  default?: unknown
}

export type GeneratorInputValue = Record<string, TypeSelectorValue | null>

export function formatGeneratorArgLabel(name: string): string {
  const words = name.replace(/([A-Z])/g, ' $1').trim()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

type GeneratorInputProps = {
  value: GeneratorInputValue | null
  onChange: (value: GeneratorInputValue | null) => void
  args: GeneratorArg[]
  description?: string
  collapseOptionalArgs?: boolean
}

export function GeneratorInput({
  value,
  onChange,
  args,
  description,
  collapseOptionalArgs = true,
}: GeneratorInputProps) {
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
        <p className="text-sm text-gray-600 dark:text-gray-400 italic">{description}</p>
      )}
      {visibleArgs.map((arg) => {
        const argVal = value?.[arg.name] ?? null
        const label = formatGeneratorArgLabel(arg.name)
        const operator = argVal ? (argVal.isDistribution ? '~' : '=') : '='
        const content = (
          <>
            <span className="text-sm italic text-gray-600">{arg.description}</span>
            <TypeSelector
              type={arg.type}
              value={argVal}
              onChange={(v) => handleArgChange(arg.name, v)}
              allowDistributions={true}
            />
          </>
        )

        if (!arg.required && collapseOptionalArgs) {
          return (
            <details key={arg.name} className="group rounded-xl border border-gray-200/70 bg-gradient-to-br from-gray-50/60 to-white p-4 dark:bg-gray-800/60">
              <summary className="flex cursor-pointer items-center gap-1">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth="1.5"
                  stroke="currentColor"
                  className="size-4 text-gray-500 rotate-180 transition-transform group-open:rotate-90"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M15.75 19.5 8.25 12l7.5-7.5"
                  />
                </svg>
                <span className="text-sm font-medium">{label}</span>
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">optional</span>
              </summary>
              <div className="mt-2 flex flex-col gap-1">
                {content}
              </div>
            </details>
          )
        }

        return (
          <div key={arg.name} className="flex flex-col gap-2 rounded-xl border border-gray-200/70 bg-gradient-to-br from-gray-50/60 to-white p-4 dark:bg-gray-800/60">
            <div className="flex items-center gap-1.5">
              <span className="text-sm font-medium">{label}</span>
              {!arg.required && <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">optional</span>}
            </div>
            {content}
          </div>
        )
      })}
    </div>
  )
}
