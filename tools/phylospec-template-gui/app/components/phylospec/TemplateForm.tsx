'use client'

import '@/app/components/phylospec'

import { useState, useEffect } from 'react'
import { TypeSelector, type TypeSelectorValue } from './TypeSelector'
import { getComponents } from './registry'
import { DefaultsContext } from './DefaultsContext'

type PlaceholderConfig = { type: string; name?: string; description?: string }

export type TemplateFormConfig = {
  experimentName?: string
  defaults?: Record<string, string>
  placeholders: Record<string, PlaceholderConfig>
}

export type TemplateFormProps = {
  template: string
  config: TemplateFormConfig
  onChange?: (resolvedTemplate: string) => void
}

type Occurrence = { start: number; end: number; placeholder: string }

function collectOccurrences(template: string, placeholders: Record<string, PlaceholderConfig>): Occurrence[] {
  const occurrences: Occurrence[] = []
  for (const placeholder of Object.keys(placeholders)) {
    let idx = 0
    while (true) {
      const pos = template.indexOf(placeholder, idx)
      if (pos === -1) break
      occurrences.push({ start: pos, end: pos + placeholder.length, placeholder })
      idx = pos + placeholder.length
    }
  }
  return occurrences.sort((a, b) => a.start - b.start)
}

function resolveTemplate(
  template: string,
  placeholders: Record<string, PlaceholderConfig>,
  values: Record<string, TypeSelectorValue | null>,
): string {
  const occurrences = collectOccurrences(template, placeholders)
  let result = ''
  let cursor = 0
  for (const { start, end, placeholder } of occurrences) {
    result += template.slice(cursor, start)
    const val = values[placeholder]
    const component = val ? getComponents(placeholders[placeholder].type).find((c) => c.id === val.componentId) : null
    // val.value is null for zero-arg generators (nothing to fill in); {} produces name()
    result += (val && component) ? component.toExpression(val.value ?? {}) : placeholder
    cursor = end
  }
  return result + template.slice(cursor)
}

type Segment = { text: string; kind: 'normal' | 'placeholder' | 'value' }

function buildSegments(
  template: string,
  placeholders: Record<string, PlaceholderConfig>,
  values: Record<string, TypeSelectorValue | null>,
): Segment[] {
  const occurrences = collectOccurrences(template, placeholders)
  const segments: Segment[] = []
  let cursor = 0
  for (const { start, end, placeholder } of occurrences) {
    if (start > cursor) {
      segments.push({ text: template.slice(cursor, start), kind: 'normal' })
    }
    const val = values[placeholder]
    const cfg = placeholders[placeholder]
    const component = val ? getComponents(cfg.type).find((c) => c.id === val.componentId) : null
    if (val && component) {
      segments.push({ text: component.toExpression(val.value ?? {}), kind: 'value' })
    } else {
      segments.push({ text: placeholder, kind: 'placeholder' })
    }
    cursor = end
  }
  if (cursor < template.length) {
    segments.push({ text: template.slice(cursor), kind: 'normal' })
  }
  return segments
}

function formatTabLabel(placeholder: string, cfg: PlaceholderConfig, index: number): string {
  if (cfg.name) return `${index + 1}. ${cfg.name}`
  const name = placeholder.replace(/^\$/, '')
  const words = name.replace(/([A-Z])/g, ' $1').trim()
  return `${index + 1}. ${words.charAt(0).toUpperCase() + words.slice(1)}`
}

export function TemplateForm({ template, config, onChange }: TemplateFormProps) {
  const { experimentName, placeholders } = config
  const placeholderEntries = Object.entries(placeholders)

  const [values, setValues] = useState<Record<string, TypeSelectorValue | null>>(() =>
    Object.fromEntries(placeholderEntries.map(([k]) => [k, null]))
  )
  const [activeTab, setActiveTab] = useState<string>(placeholderEntries[0]?.[0] ?? '')

  const resolvedTemplate = resolveTemplate(template, placeholders, values)
  const segments = buildSegments(template, placeholders, values)

  useEffect(() => {
    onChange?.(resolvedTemplate)
  }, [resolvedTemplate])

  function handleChange(placeholder: string, v: TypeSelectorValue) {
    setValues((prev) => ({ ...prev, [placeholder]: v }))
  }

  return (
    <DefaultsContext.Provider value={config.defaults ?? {}}>
    <div className="flex flex-col gap-4 font-mono text-sm">

      {experimentName && (
        <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">{experimentName}</h2>
      )}

      <div className="flex items-start gap-8">
        <div className="flex flex-col gap-3 flex-1">

          {/* tab bar */}
          <div className="flex gap-1 border-b border-gray-200 dark:border-gray-700">
            {placeholderEntries.map(([placeholder, cfg], index) => (
              <button
                key={placeholder}
                onClick={() => setActiveTab(placeholder)}
                className={`px-3 py-1.5 text-xs font-medium border-b-2 -mb-px transition-colors ${
                  placeholder === activeTab
                    ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
                    : 'border-transparent text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
                }`}
              >
                {formatTabLabel(placeholder, cfg, index)}
              </button>
            ))}
          </div>

          {/* tab content — all mounted, inactive ones hidden to preserve internal state */}
          {placeholderEntries.map(([placeholder, cfg]) => (
            <div key={placeholder} className={`flex flex-col gap-3${placeholder !== activeTab ? ' hidden' : ''}`}>
              {cfg.description && (
                <p className="text-xs text-gray-500 dark:text-gray-400">{cfg.description}</p>
              )}
              <TypeSelector
                type={cfg.type}
                value={values[placeholder] ?? null}
                onChange={(v) => handleChange(placeholder, v)}
                allowDistributions={false}
              />
            </div>
          ))}

        </div>

        <pre className="w-1/3 shrink-0 whitespace-pre-wrap rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm dark:border-gray-700 dark:bg-gray-900">
          {segments.map((seg, i) =>
            seg.kind === 'placeholder' ? (
              <span key={i} className="text-orange-500 dark:text-orange-400">{seg.text}</span>
            ) : seg.kind === 'value' ? (
              <span key={i} className="font-semibold text-blue-600 dark:text-blue-400">{seg.text}</span>
            ) : (
              seg.text
            )
          )}
        </pre>
      </div>
    </div>
    </DefaultsContext.Provider>
  )
}
