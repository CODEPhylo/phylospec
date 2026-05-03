'use client'

import '@/app/components/phylospec'

import { useState, useEffect } from 'react'
import { TypeSelector, type TypeSelectorValue } from './TypeSelector'
import { getComponents } from './registry'
import { indentContinuation } from './generator/expression'
import { DefaultsContext } from './DefaultsContext'

type PlaceholderConfig = { type: string; name?: string; description?: string }

type TabGroup = { name: string; description?: string; placeholders: string[] }

export type TemplateFormConfig = {
  experimentName?: string
  defaults?: Record<string, string>
  placeholders: Record<string, PlaceholderConfig>
  groups?: TabGroup[]
}

type Tab =
  | { kind: 'single'; placeholder: string }
  | { kind: 'group'; name: string; description?: string; placeholders: string[] }

function buildTabs(placeholders: Record<string, PlaceholderConfig>, groups: TabGroup[] = []): Tab[] {
  const grouped = new Map<string, TabGroup>()
  for (const g of groups) {
    for (const p of g.placeholders) grouped.set(p, g)
  }

  const tabs: Tab[] = []
  const emittedGroups = new Set<TabGroup>()
  for (const placeholder of Object.keys(placeholders)) {
    const g = grouped.get(placeholder)
    if (g) {
      if (!emittedGroups.has(g)) {
        emittedGroups.add(g)
        tabs.push({ kind: 'group', name: g.name, description: g.description, placeholders: g.placeholders })
      }
    } else {
      tabs.push({ kind: 'single', placeholder })
    }
  }
  return tabs
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

function colAt(s: string): number {
  const nl = s.lastIndexOf('\n')
  return nl === -1 ? s.length : s.length - nl - 1
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
    if (val && component) {
      result += indentContinuation(component.toExpression(val.value ?? {}), colAt(result))
    } else {
      result += placeholder
    }
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
  let accumulated = ''
  for (const { start, end, placeholder } of occurrences) {
    if (start > cursor) {
      const text = template.slice(cursor, start)
      segments.push({ text, kind: 'normal' })
      accumulated += text
    }
    const val = values[placeholder]
    const cfg = placeholders[placeholder]
    const component = val ? getComponents(cfg.type).find((c) => c.id === val.componentId) : null
    if (val && component) {
      const text = indentContinuation(component.toExpression(val.value ?? {}), colAt(accumulated))
      segments.push({ text, kind: 'value' })
      accumulated += text
    } else {
      segments.push({ text: placeholder, kind: 'placeholder' })
      accumulated += placeholder
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
  const { placeholders } = config
  const placeholderEntries = Object.entries(placeholders)
  const tabs = buildTabs(placeholders, config.groups)

  const [values, setValues] = useState<Record<string, TypeSelectorValue | null>>(() =>
    Object.fromEntries(placeholderEntries.map(([k]) => [k, null]))
  )
  const [activeTab, setActiveTab] = useState<string>(() => {
    const first = tabs[0]
    return first ? (first.kind === 'single' ? first.placeholder : first.placeholders[0]) : ''
  })

  const resolvedTemplate = resolveTemplate(template, placeholders, values)
  const segments = buildSegments(template, placeholders, values)
  const allResolved = segments.every(seg => seg.kind !== 'placeholder')

  function handleExport() {
    const blob = new Blob([resolvedTemplate], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'model.phylospec'
    a.click()
    URL.revokeObjectURL(url)
  }

  useEffect(() => {
    onChange?.(resolvedTemplate)
  }, [resolvedTemplate])

  function handleChange(placeholder: string, v: TypeSelectorValue) {
    setValues((prev) => ({ ...prev, [placeholder]: v }))
  }

  return (
    <DefaultsContext.Provider value={config.defaults ?? {}}>
    <div className="flex flex-col gap-4 text-base">
      <div className="flex items-start gap-8">
        <div className="flex flex-col gap-3 flex-1">

          {/* tab bar */}
          <div className="flex gap-1 border-b border-gray-200/70 dark:border-gray-700">
            {tabs.map((tab, index) => {
              const tabKey = tab.kind === 'single' ? tab.placeholder : tab.placeholders[0]
              const label = tab.kind === 'group'
                ? `${index + 1}. ${tab.name}`
                : formatTabLabel(tab.placeholder, placeholders[tab.placeholder], index)
              const isActive = activeTab === tabKey
              return (
                <button
                  key={tabKey}
                  onClick={() => setActiveTab(tabKey)}
                  className={`px-5 py-2 border-b-2 -mb-px transition-all active:scale-95 ${
                    isActive
                      ? 'font-semibold border-accent text-accent'
                      : 'font-medium border-transparent text-gray-400 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-200'
                  }`}
                >
                  {label}
                </button>
              )
            })}
          </div>

          {/* tab content — all mounted, inactive ones hidden to preserve internal state */}
          {tabs.map((tab) => {
            const tabKey = tab.kind === 'single' ? tab.placeholder : tab.placeholders[0]
            const isActive = activeTab === tabKey
            const items = tab.kind === 'single'
              ? [tab.placeholder]
              : tab.placeholders
            return (
              <div key={tabKey} className={`flex flex-col gap-3${!isActive ? ' hidden' : ''}`}>
                {tab.kind === 'group' && tab.description && (
                  <p className="text-sm text-gray-600 dark:text-gray-400">{tab.description}</p>
                )}
                {items.map((placeholder) => {
                  const cfg = placeholders[placeholder]
                  const isGroup = tab.kind === 'group'
                  return (
                    <div key={placeholder} className={isGroup ? 'flex flex-col gap-2 rounded-lg border border-gray-200/70 bg-gray-50/50 p-4 dark:border-gray-700 dark:bg-gray-800/30' : 'flex flex-col gap-3'}>
                      {isGroup && (
                        <span className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                          {cfg.name ?? placeholder.replace(/^\$/, '')}
                        </span>
                      )}
                      {cfg.description && (
                        <p className="text-sm text-gray-600 dark:text-gray-400">{cfg.description}</p>
                      )}
                      <TypeSelector
                        type={cfg.type}
                        value={values[placeholder] ?? null}
                        onChange={(v) => handleChange(placeholder, v)}
                        allowDistributions={false}
                      />
                    </div>
                  )
                })}
              </div>
            )
          })}

        </div>

        <div className="flex w-2/5 shrink-0 flex-col gap-2">
          <div className="flex items-center ml-1">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-400 flex-1">PhyloSpec Model Description</span>
            <button
              onClick={() => navigator.clipboard.writeText(resolvedTemplate)}
              title="Copy to clipboard"
              className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors active:scale-95"
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor" className="size-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.666 3.888A2.25 2.25 0 0 0 13.5 2.25h-3c-1.03 0-1.9.693-2.166 1.638m7.332 0c.055.194.084.4.084.612v0a.75.75 0 0 1-.75.75H9a.75.75 0 0 1-.75-.75v0c0-.212.03-.418.084-.612m7.332 0c.646.049 1.288.11 1.927.184 1.1.128 1.907 1.077 1.907 2.185V19.5a2.25 2.25 0 0 1-2.25 2.25H6.75A2.25 2.25 0 0 1 4.5 19.5V6.257c0-1.108.806-2.057 1.907-2.185a48.208 48.208 0 0 1 1.927-.184" />
              </svg>
            </button>
          </div>
          <pre className="whitespace-pre-wrap rounded-xl border border-gray-200/70 bg-gradient-to-br from-gray-50 to-white p-5 font-mono text-base shadow-sm dark:border-gray-700 dark:bg-gray-900">
          {segments.map((seg, i) =>
            seg.kind === 'placeholder' ? (
              <span key={i} className="text-cyan-600 dark:text-cyan-400">{seg.text}</span>
            ) : seg.kind === 'value' ? (
              <span key={i} className="font-semibold text-accent">{seg.text}</span>
            ) : (
              seg.text
            )
          )}
        </pre>
          <button
            onClick={handleExport}
            disabled={!allResolved}
            className={`w-full rounded-lg px-4 py-2.5 text-sm font-semibold transition-all bg-gradient-to-r from-accent to-teal-500 text-white shadow-sm ${allResolved ? 'opacity-100 hover:opacity-90 hover:-translate-y-px hover:shadow-md active:scale-[0.98] active:opacity-75 cursor-pointer' : 'opacity-50 cursor-not-allowed'}`}
          >
            Export model.phylospec
          </button>
        </div>
      </div>
    </div>
    </DefaultsContext.Provider>
  )
}
