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

export function TypeSelector({ type, value, onChange, allowDistributions = false }: TypeSelectorProps) {
  const distComponents = allowDistributions ? getComponents(`Distribution<${resolveAlias(type)}>`) : []
  const fixedComponents = getComponents(type)
  const distIds = new Set(distComponents.map((c) => c.id))

  const showModePicker = allowDistributions && distComponents.length > 0 && fixedComponents.length > 0

  const initialMode = value?.isDistribution ? 'prior' : 'fixed'
  const [mode, setMode] = useState<'fixed' | 'prior'>(initialMode)
  const [selectedId, setSelectedId] = useState<string | null>(value?.componentId ?? null)

  // when mode picker is shown, restrict to the active bucket; otherwise merge (dist first)
  const activeComponents = showModePicker
    ? (mode === 'prior' ? distComponents : fixedComponents)
    : [...distComponents, ...fixedComponents]

  if (activeComponents.length === 0 && !showModePicker) {
    return <span className="text-xs text-gray-400">No components registered for type &quot;{type}&quot;</span>
  }

  // only honour selectedId if it actually exists in the current bucket
  const validSelectedId = activeComponents.some((c) => c.id === selectedId) ? selectedId : null
  // auto-select only when there is no mode picker (single-bucket); with the mode picker shown the user must pick explicitly
  const effectiveId = validSelectedId ?? (!showModePicker && activeComponents.length === 1 ? activeComponents[0].id : null)
  const registration = effectiveId ? (activeComponents.find((c) => c.id === effectiveId) ?? null) : null
  const currentValue = value?.componentId === effectiveId ? value.value : null

  function handleModeSwitch(newMode: 'fixed' | 'prior') {
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

      {/* mode toggle: fixed value vs. prior distribution */}
      {showModePicker && (
        <div className="flex rounded overflow-hidden border border-gray-200 w-fit text-xs dark:border-gray-700">
          <button
            onClick={() => handleModeSwitch('fixed')}
            className={`px-2.5 py-0.5 font-mono ${
              mode === 'fixed'
                ? 'bg-blue-600 text-white'
                : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'
            }`}
          >
            = fixed
          </button>
          <button
            onClick={() => handleModeSwitch('prior')}
            className={`px-2.5 py-0.5 font-mono border-l border-gray-200 dark:border-gray-700 ${
              mode === 'prior'
                ? 'bg-amber-500 text-white'
                : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'
            }`}
          >
            ~ prior
          </button>
        </div>
      )}

      {/* component picker within the active mode — always show when mode picker is visible */}
      {(activeComponents.length > 1 || showModePicker) && (
        <div className="flex flex-wrap gap-1">
          {activeComponents.map((c) => (
            <button
              key={c.id}
              onClick={() => handleSelect(c.id)}
              className={`rounded px-2 py-0.5 text-xs ${
                c.id === effectiveId
                  ? (mode === 'prior' && showModePicker ? 'bg-amber-500 text-white' : 'bg-blue-600 text-white')
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
