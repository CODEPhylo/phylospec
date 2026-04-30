'use client'

import '@/app/components/phylospec'

import { useState, useEffect } from 'react'
import { TypeSelector, type TypeSelectorValue } from './TypeSelector'
import { getComponents } from './registry'

type PlaceholderConfig = { type: string }

export type TemplateFormProps = {
  template: string
  config: Record<string, PlaceholderConfig>
  onChange?: (resolvedTemplate: string) => void
}

function resolveTemplate(
  template: string,
  config: Record<string, PlaceholderConfig>,
  values: Record<string, TypeSelectorValue | null>,
): string {
  let result = template
  for (const [placeholder, { type }] of Object.entries(config)) {
    const val = values[placeholder]
    if (!val) continue
    const component = getComponents(type).find((c) => c.id === val.componentId)
    if (!component) continue
    // val.value is null for zero-arg generators (nothing to fill in); {} produces name()
    result = result.replaceAll(placeholder, component.toExpression(val.value ?? {}))
  }
  return result
}

export function TemplateForm({ template, config, onChange }: TemplateFormProps) {
  const [values, setValues] = useState<Record<string, TypeSelectorValue | null>>(() =>
    Object.fromEntries(Object.keys(config).map((k) => [k, null]))
  )

  const resolvedTemplate = resolveTemplate(template, config, values)

  useEffect(() => {
    onChange?.(resolvedTemplate)
  }, [resolvedTemplate])

  function handleChange(placeholder: string, v: TypeSelectorValue) {
    setValues((prev) => ({ ...prev, [placeholder]: v }))
  }

  const placeholders = Object.entries(config)

  return (
    <div className="flex items-start gap-8 font-mono text-sm">
      <div className="flex flex-col gap-6">
        {placeholders.map(([placeholder, { type }]) => (
          <div key={placeholder} className="flex flex-col gap-1">
            <span className="font-semibold text-gray-800 dark:text-gray-200">
              {placeholder.replace(/^\$/, '')}
            </span>
            <TypeSelector
              type={type}
              value={values[placeholder] ?? null}
              onChange={(v) => handleChange(placeholder, v)}
            />
          </div>
        ))}
      </div>
      <pre className="flex-1 whitespace-pre-wrap rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm dark:border-gray-700 dark:bg-gray-900">
        {resolvedTemplate}
      </pre>
    </div>
  )
}
