'use client'

import { useState } from 'react'
import { getComponents, resolveAlias } from './registry'

export type TypeSelectorValue = {
  componentId: string
  value: unknown
  isDistribution: boolean
}

type TypeSelectorProps = {
  type: string
  value: TypeSelectorValue | null
  onChange: (v: TypeSelectorValue) => void
  allowDistributions?: boolean
}

type Mode = 'fixed' | 'estimated' | 'calculated'

const MODE_CONFIG: Record<Mode, { label: string; activeClass: string }> = {
  fixed:      { label: '= fixed',      activeClass: 'bg-accent text-white' },
  estimated:  { label: '~ estimated',  activeClass: 'bg-cyan-600 text-white' },
  calculated: { label: 'ƒ calculated', activeClass: 'bg-violet-600 text-white' },
}

function deriveInitialMode(
  value: TypeSelectorValue | null,
  calcIds: Set<string>,
  availableModes: Mode[],
): Mode {
  if (!value) return availableModes[0] ?? 'fixed'
  if (value.isDistribution) return 'estimated'
  if (calcIds.has(value.componentId)) return 'calculated'
  return availableModes[0] ?? 'fixed'
}

export function TypeSelector({ type, value, onChange, allowDistributions = false }: TypeSelectorProps) {
  const allForType = getComponents(type)
  const literalComponents = allForType.filter(c => c.isLiteral)
  const calcComponents    = allForType.filter(c => !c.isLiteral)
  const distComponents    = allowDistributions
    ? getComponents(`Distribution<${resolveAlias(type)}>`)
    : []

  const availableModes: Mode[] = [
    ...(literalComponents.length > 0 ? (['fixed']      as Mode[]) : []),
    ...(distComponents.length > 0    ? (['estimated']  as Mode[]) : []),
    ...(calcComponents.length > 0    ? (['calculated'] as Mode[]) : []),
  ]

  const calcIds = new Set(calcComponents.map(c => c.id))
  const distIds = new Set(distComponents.map(c => c.id))

  const [mode, setMode]           = useState<Mode>(() => deriveInitialMode(value, calcIds, availableModes))
  const [selectedId, setSelectedId] = useState<string | null>(value?.componentId ?? null)

  if (availableModes.length === 0) {
    return <span className="text-sm text-gray-500">No components registered for type &quot;{type}&quot;</span>
  }

  const activeBucket =
    mode === 'fixed'      ? literalComponents :
    mode === 'estimated'  ? distComponents :
                            calcComponents

  // only honour selectedId if it exists in the current bucket
  const validSelectedId = activeBucket.some(c => c.id === selectedId) ? selectedId : null
  const showModePicker = availableModes.length > 1
  // auto-select when there is no mode picker, or when in fixed mode with a single component
  const effectiveId = validSelectedId ?? (
    activeBucket.length === 1 && (!showModePicker || mode === 'fixed') ? activeBucket[0].id : null
  )
  const registration = effectiveId ? (activeBucket.find(c => c.id === effectiveId) ?? null) : null
  const currentValue = value?.componentId === effectiveId ? value.value : null

  function handleModeSwitch(newMode: Mode) {
    setMode(newMode)
    setSelectedId(null)
  }

  function handleSelect(id: string) {
    setSelectedId(id)
    onChange({ componentId: id, value: null, isDistribution: distIds.has(id) })
  }

  function handleChange(v: unknown) {
    onChange({ componentId: effectiveId!, value: v, isDistribution: distIds.has(effectiveId!) })
  }

  return (
    <div className="flex flex-col gap-2">

      {showModePicker && (
        <div className="flex w-fit overflow-hidden rounded border border-gray-200 text-sm dark:border-gray-700">
          {availableModes.map((m, i) => (
            <button
              key={m}
              onClick={() => handleModeSwitch(m)}
              className={`px-3 py-1 ${i > 0 ? 'border-l border-gray-200 dark:border-gray-700' : ''} ${
                mode === m
                  ? MODE_CONFIG[m].activeClass
                  : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'
              }`}
            >
              {MODE_CONFIG[m].label}
            </button>
          ))}
        </div>
      )}

      {mode === 'estimated' && <span className="text-sm text-gray-500">Choose a prior:</span>}
      {mode === 'calculated' && <span className="text-sm text-gray-500">Choose a function:</span>}

      {(activeBucket.length > 1 || (showModePicker && !(mode === 'fixed' && activeBucket.length === 1))) && (
        <div className="flex flex-wrap gap-1">
          {activeBucket.map((c) => (
            <button
              key={c.id}
              onClick={() => handleSelect(c.id)}
              className={`rounded px-2.5 py-1 text-sm ${
                c.id === effectiveId
                  ? MODE_CONFIG[mode].activeClass
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
              }`}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}

      {registration && (
        <registration.Component
          value={currentValue}
          onChange={handleChange}
        />
      )}
    </div>
  )
}
