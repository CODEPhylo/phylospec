'use client'

import '@/app/components/phylospec'

import { useState, useEffect } from 'react'
import { TypeSelector, type TypeSelectorValue } from './TypeSelector'
import { getComponents } from './registry'

type PlaceholderConfig = { type: string; description?: string }

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
  const placeholders = Object.entries(config)

  const [values, setValues] = useState<Record<string, TypeSelectorValue | null>>(() =>
    Object.fromEntries(placeholders.map(([k]) => [k, null]))
  )
  const [activeTab, setActiveTab] = useState<string>(placeholders[0]?.[0] ?? '')

  const resolvedTemplate = resolveTemplate(template, config, values)

  useEffect(() => {
    onChange?.(resolvedTemplate)
  }, [resolvedTemplate])

  function handleChange(placeholder: string, v: TypeSelectorValue) {
    setValues((prev) => ({ ...prev, [placeholder]: v }))
  }

  const activeConfig = config[activeTab]

  return (
    <div className="flex items-start gap-8 font-mono text-sm">
      <div className="flex flex-col gap-3 flex-1">

        {/* tab bar */}
        <div className="flex gap-1 border-b border-gray-200 dark:border-gray-700">
          {placeholders.map(([placeholder]) => (
            <button
              key={placeholder}
              onClick={() => setActiveTab(placeholder)}
              className={`px-3 py-1.5 text-xs font-medium border-b-2 -mb-px transition-colors ${
                placeholder === activeTab
                  ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
                  : 'border-transparent text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
              }`}
            >
              {placeholder.replace(/^\$/, '')}
            </button>
          ))}
        </div>

        {/* active tab content */}
        {activeConfig && (
          <div className="flex flex-col gap-3">
            {activeConfig.description && (
              <p className="text-xs text-gray-500 dark:text-gray-400">{activeConfig.description}</p>
            )}
            <TypeSelector
              type={activeConfig.type}
              value={values[activeTab] ?? null}
              onChange={(v) => handleChange(activeTab, v)}
            />
          </div>
        )}

      </div>

      <pre className="w-1/3 shrink-0 whitespace-pre-wrap rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm dark:border-gray-700 dark:bg-gray-900">
        {resolvedTemplate}
      </pre>
    </div>
  )
}
